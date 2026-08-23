package com.example.newsmaterial.upload;

import com.example.newsmaterial.common.api.ApiException;
import com.example.newsmaterial.file.FilePolicy;
import com.example.newsmaterial.security.*;
import com.example.newsmaterial.storage.LocalStorageAdapter;
import com.example.newsmaterial.storage.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class UploadIntentService {
    private static final Pattern SHA=Pattern.compile("[0-9a-fA-F]{64}");
    private final UploadIntentRepository intents;
    private final UserRepository users;
    private final StoragePort storage;
    private final FilePolicy policy;
    private final Duration ttl;

    public UploadIntentService(UploadIntentRepository intents, UserRepository users, StoragePort storage,
                               FilePolicy policy,
                               @Value("${app.upload.intent-ttl:PT15M}") Duration ttl) {
        this.intents=intents; this.users=users; this.storage=storage; this.policy=policy; this.ttl=ttl;
    }

    @Transactional
    public List<IntentResult> create(UUID userId,List<UploadFileSpec> files){
        if(files==null||files.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_UPLOAD_REQUEST","At least one file is required");
        List<Long> sizes=new ArrayList<>();
        for(UploadFileSpec f:files){
            if(f==null||invalidText(f.originalFilename(),255)||invalidText(f.mimeType(),160)
                    ||f.checksumSha256()==null||!SHA.matcher(f.checksumSha256()).matches()){
                throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_UPLOAD_REQUEST","File metadata is invalid");
            }
            sizes.add(f.sizeBytes());
            validateMime(f.mimeType());
        }
        policy.validateSnapshot(sizes);
        AppUser user=users.findById(userId).orElseThrow(); Instant now=Instant.now(),expires=now.plus(ttl); List<IntentResult> result=new ArrayList<>();
        for(UploadFileSpec f:files){UUID id=UUID.randomUUID();String key="uploads/"+userId+"/"+UUID.randomUUID()+"/"+safe(f.originalFilename()); UploadIntent intent=intents.save(new UploadIntent(id,user,key,f.originalFilename().trim(),f.mimeType().trim().toLowerCase(),f.sizeBytes(),f.checksumSha256().toLowerCase(),expires,now)); StoragePort.UploadTarget target=storage.createUploadTarget(id,key,intent.getMimeType(),intent.getSizeBytes(),intent.getChecksumSha256(),expires);result.add(new IntentResult(id,key,target.uploadUrl(),target.requiredHeaders(),expires));}
        return result;
    }

    @Transactional public void localPut(UUID id,UUID userId,String mime,byte[] bytes,LocalStorageAdapter local){UploadIntent i=ownedPending(id,userId);if(Instant.now().isAfter(i.getExpiresAt()))throw new ApiException(HttpStatus.GONE,"UPLOAD_INTENT_EXPIRED","Upload intent has expired");if(!i.getMimeType().equalsIgnoreCase(mime)||i.getSizeBytes()!=bytes.length)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"UPLOAD_METADATA_MISMATCH","Uploaded object metadata does not match the intent");String actual=sha(bytes);if(!actual.equals(i.getChecksumSha256()))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"UPLOAD_METADATA_MISMATCH","Uploaded object checksum does not match the intent");local.put(i.getObjectKey(),i.getMimeType(),bytes);}

    @Transactional
    public List<UploadIntent> verifyForUse(UUID userId,List<UUID> ids){
        if(ids==null||ids.isEmpty()||new HashSet<>(ids).size()!=ids.size())throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_FILE_SET","Distinct files are required");
        policy.validateFileCount(ids.size());
        List<UploadIntent> found=new ArrayList<>();
        for(UUID id:ids){UploadIntent i=ownedPending(id,userId);if(Instant.now().isAfter(i.getExpiresAt()))throw new ApiException(HttpStatus.GONE,"UPLOAD_INTENT_EXPIRED","Upload intent has expired");StoragePort.StoredObject actual=storage.inspect(i.getObjectKey());if(actual.sizeBytes()!=i.getSizeBytes()||!i.getMimeType().equalsIgnoreCase(actual.mimeType())||actual.checksumSha256()==null||!i.getChecksumSha256().equalsIgnoreCase(actual.checksumSha256()))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"UPLOAD_NOT_VERIFIED","Uploaded object does not match its intent");found.add(i);}
        return found;
    }

    private UploadIntent ownedPending(UUID id,UUID userId){UploadIntent i=intents.findByIdForUpdate(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"UPLOAD_INTENT_NOT_FOUND","Upload intent was not found"));if(!i.getUser().getId().equals(userId))throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","You do not have permission for this action");if(i.getStatus()!=UploadIntentStatus.PENDING)throw new ApiException(HttpStatus.CONFLICT,"UPLOAD_INTENT_CONSUMED","Upload intent has already been used");return i;}
    private void validateMime(String mime){String m=mime==null?"":mime.toLowerCase();if(!(m.startsWith("image/")||m.startsWith("video/")||m.startsWith("audio/")||m.equals("application/pdf")||m.equals("text/plain")))throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"UNSUPPORTED_FILE_TYPE","File type is not supported");}
    private boolean invalidText(String value,int max){return value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(Character::isISOControl);}
    private String safe(String name){return name.replaceAll("[^A-Za-z0-9._-]","_");}
    private String sha(byte[] bytes){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    public record UploadFileSpec(String originalFilename,String mimeType,long sizeBytes,String checksumSha256){}
    public record IntentResult(UUID intentId,String objectKey,String uploadUrl,Map<String,String> requiredHeaders,Instant expiresAt){}
}
