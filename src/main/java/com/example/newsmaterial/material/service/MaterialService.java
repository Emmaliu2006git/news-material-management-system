package com.example.newsmaterial.material.service;

import com.example.newsmaterial.analysis.ImageAnalysis;
import com.example.newsmaterial.analysis.ImageAnalysisRepository;
import com.example.newsmaterial.analysis.ImageAnalysisService;
import com.example.newsmaterial.common.api.ApiException;
import com.example.newsmaterial.file.*;
import com.example.newsmaterial.material.api.MaterialApi.*;
import com.example.newsmaterial.material.domain.*;
import com.example.newsmaterial.material.persistence.*;
import com.example.newsmaterial.project.*;
import com.example.newsmaterial.security.*;
import com.example.newsmaterial.search.SearchDocumentService;
import com.example.newsmaterial.upload.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class MaterialService {
    private static final String CREATE_TEXT="CREATE_TEXT", CREATE_FILE="CREATE_FILE", CREATE_VERSION="CREATE_VERSION";
    private final MaterialRepository materials;
    private final MaterialVersionRepository versions;
    private final IdempotencyRecordRepository idempotency;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final UploadIntentService uploads;
    private final FileAssetRepository files;
    private final VersionFileRepository versionFiles;
    private final ImageAnalysisService imageAnalysisService;
    private final ImageAnalysisRepository imageAnalyses;
    private final SearchDocumentService searchDocuments;

    public MaterialService(MaterialRepository materials, MaterialVersionRepository versions, IdempotencyRecordRepository idempotency,
                           ProjectRepository projects, UserRepository users, UploadIntentService uploads,
                           FileAssetRepository files, VersionFileRepository versionFiles,
                           ImageAnalysisService imageAnalysisService, ImageAnalysisRepository imageAnalyses,
                           SearchDocumentService searchDocuments) {
        this.materials=materials; this.versions=versions; this.idempotency=idempotency; this.projects=projects; this.users=users;
        this.uploads=uploads; this.files=files; this.versionFiles=versionFiles;
        this.imageAnalysisService=imageAnalysisService; this.imageAnalyses=imageAnalyses;
        this.searchDocuments=searchDocuments;
    }

    @Transactional
    public MaterialView createText(UUID userId,String key,TextMaterialRequest request){
        String fp=fingerprint(request.projectId(),request.title(),request.eventTime(),request.location(),request.manualTags(),request.textBody());
        AppUser owner=users.findByIdForUpdate(userId).orElseThrow(); MaterialView replay=replay(userId,CREATE_TEXT,key,fp); if(replay!=null)return replay;
        ReportingProject project=project(request.projectId()); Instant now=Instant.now(); UUID versionId=UUID.randomUUID();
        Material material=new Material(UUID.randomUUID(),project,owner,MaterialType.TEXT,now); material.setCurrentVersionId(versionId); materials.save(material);
        MaterialVersion first=versions.save(new MaterialVersion(versionId,material,1,owner,now,clean(request.title()),request.eventTime(),nullableClean(request.location()),tags(request.manualTags()),clean(request.textBody())));
        material.setCurrentVersionId(first.getId()); searchDocuments.rebuildCurrent(material,first); idempotency.save(new IdempotencyRecord(UUID.randomUUID(),userId,CREATE_TEXT,key,fp,material.getId(),1,now));
        return view(material,first,userId,false);
    }

    @Transactional
    public MaterialView createFile(UUID userId,String key,FileMaterialRequest request){
        String fp=fingerprint(request.projectId(),request.title(),request.eventTime(),request.location(),request.manualTags(),request.textContent(),request.uploadIntentIds());
        AppUser owner=users.findByIdForUpdate(userId).orElseThrow(); MaterialView replay=replay(userId,CREATE_FILE,key,fp); if(replay!=null)return replay;
        List<UploadIntent> intents=uploads.verifyForUse(userId,request.uploadIntentIds()); ReportingProject project=project(request.projectId());
        uploads.validateSnapshotTotal(intents.stream().map(UploadIntent::getSizeBytes).toList());
        Instant now=Instant.now(); UUID versionId=UUID.randomUUID(); Material material=new Material(UUID.randomUUID(),project,owner,MaterialType.FILE,now);
        material.setCurrentVersionId(versionId); materials.save(material);
        MaterialVersion first=versions.save(MaterialVersion.file(versionId,material,1,owner,now,clean(request.title()),request.eventTime(),nullableClean(request.location()),tags(request.manualTags()),nullableClean(request.textContent())));
        attachNew(material,first,owner,intents,0,now); material.setCurrentVersionId(first.getId());
        searchDocuments.rebuildCurrent(material,first); idempotency.save(new IdempotencyRecord(UUID.randomUUID(),userId,CREATE_FILE,key,fp,material.getId(),1,now)); return view(material,first,userId,false);
    }

    @Transactional(readOnly=true) public MaterialView current(UUID id,UUID userId){Material m=material(id);return view(m,versions.findById(m.getCurrentVersionId()).orElseThrow(),userId,false);}
    @Transactional(readOnly=true) public List<VersionView> history(UUID id){material(id);return versions.findByMaterial_IdOrderByVersionNumberAsc(id).stream().map(this::versionView).toList();}
    @Transactional(readOnly=true) public MaterialView historical(UUID id,int number,UUID userId){Material m=material(id);MaterialVersion v=versions.findByMaterial_IdAndVersionNumber(id,number).orElseThrow(()->missing("VERSION_NOT_FOUND","Version was not found"));return view(m,v,userId,!v.getId().equals(m.getCurrentVersionId()));}

    @Transactional
    public MaterialView createVersion(UUID materialId,UUID userId,String key,TextVersionRequest request){
        return createVersion(materialId,userId,key,new MaterialVersionRequest(request.expectedCurrentVersion(),request.title(),request.eventTime(),
                request.location(),request.manualTags(),request.textBody(),null,null,null,null));
    }

    @Transactional
    public MaterialView createVersion(UUID materialId,UUID userId,String key,MaterialVersionRequest request){
        String fp=fingerprint(materialId,request.expectedCurrentVersion(),request.title(),request.eventTime(),request.location(),request.manualTags(),request.textBody(),request.textContent(),request.fileIds(),request.uploadIntentIds(),request.files());
        Material material=materials.findByIdForUpdate(materialId).orElseThrow(()->missing("MATERIAL_NOT_FOUND","Material was not found"));
        MaterialView replay=replay(userId,CREATE_VERSION,key,fp); if(replay!=null)return replay;
        if(!material.getOwner().getId().equals(userId))throw forbidden(); MaterialVersion current=versions.findById(material.getCurrentVersionId()).orElseThrow();
        if(!Objects.equals(request.expectedCurrentVersion(),current.getVersionNumber()))throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","The material has a newer version");
        AppUser owner=users.findById(userId).orElseThrow(); Instant now=Instant.now(); MaterialVersion next;
        if(material.getType()==MaterialType.TEXT){
            if(request.textBody()==null||request.textBody().isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Text body is required");
            next=versions.save(new MaterialVersion(UUID.randomUUID(),material,current.getVersionNumber()+1,owner,now,clean(request.title()),request.eventTime(),nullableClean(request.location()),tags(request.manualTags()),clean(request.textBody())));
        }else{
            List<FileSelectionRequest> selections=fileSelections(request);
            if(selections.isEmpty()||selections.size()>10)invalidFiles();
            List<FileAsset> currentFiles=versionFiles.findByVersion_IdOrderByDisplayOrder(current.getId()).stream().map(VersionFile::getFile).toList();
            Map<UUID,FileAsset> allowed=new HashMap<>();currentFiles.forEach(f->allowed.put(f.getId(),f));
            Set<UUID> retainedIds=new HashSet<>();List<UUID> newIds=new ArrayList<>();
            for(FileSelectionRequest selection:selections){
                if(selection==null||selection.kind()==null)invalidFiles();
                if(selection.kind()==FileSelectionKind.EXISTING){
                    if(selection.fileId()==null||selection.uploadIntentId()!=null||!retainedIds.add(selection.fileId())||!allowed.containsKey(selection.fileId()))invalidFiles();
                }else{
                    if(selection.uploadIntentId()==null||selection.fileId()!=null)invalidFiles();
                    newIds.add(selection.uploadIntentId());
                }
            }
            List<UploadIntent> newIntents=newIds.isEmpty()?List.of():uploads.verifyForUse(userId,newIds);
            Map<UUID,UploadIntent> intentById=new HashMap<>();newIntents.forEach(i->intentById.put(i.getId(),i));
            List<Long> snapshotSizes=new ArrayList<>();
            for(FileSelectionRequest selection:selections)snapshotSizes.add(selection.kind()==FileSelectionKind.EXISTING?allowed.get(selection.fileId()).getSizeBytes():intentById.get(selection.uploadIntentId()).getSizeBytes());
            uploads.validateSnapshotTotal(snapshotSizes);
            next=versions.save(MaterialVersion.file(UUID.randomUUID(),material,current.getVersionNumber()+1,owner,now,clean(request.title()),request.eventTime(),nullableClean(request.location()),tags(request.manualTags()),nullableClean(request.textContent())));
            int order=0;
            for(FileSelectionRequest selection:selections){
                FileAsset file=selection.kind()==FileSelectionKind.EXISTING?allowed.get(selection.fileId()):newFile(material,owner,intentById.get(selection.uploadIntentId()),now);
                versionFiles.save(new VersionFile(next,file,order++));
            }
        }
        material.setCurrentVersionId(next.getId());searchDocuments.rebuildCurrent(material,next);idempotency.save(new IdempotencyRecord(UUID.randomUUID(),userId,CREATE_VERSION,key,fp,materialId,next.getVersionNumber(),now));return view(material,next,userId,false);
    }

    private List<FileSelectionRequest> fileSelections(MaterialVersionRequest request){
        boolean combined=request.files()!=null,legacy=(request.fileIds()!=null&&!request.fileIds().isEmpty())||(request.uploadIntentIds()!=null&&!request.uploadIntentIds().isEmpty());
        if(combined&&legacy)invalidFiles();
        if(combined)return request.files();
        List<FileSelectionRequest> result=new ArrayList<>();
        if(request.fileIds()!=null)request.fileIds().forEach(id->result.add(new FileSelectionRequest(FileSelectionKind.EXISTING,id,null)));
        if(request.uploadIntentIds()!=null)request.uploadIntentIds().forEach(id->result.add(new FileSelectionRequest(FileSelectionKind.UPLOAD,null,id)));
        return result;
    }
    private FileAsset newFile(Material material,AppUser owner,UploadIntent intent,Instant now){FileAsset file=files.save(new FileAsset(UUID.randomUUID(),material,owner,intent,now));imageAnalysisService.ensurePending(file,now);intent.consume(now);return file;}
    private void attachNew(Material material,MaterialVersion version,AppUser owner,List<UploadIntent> intents,int start,Instant now){int order=start;for(UploadIntent intent:intents){FileAsset f=newFile(material,owner,intent,now);versionFiles.save(new VersionFile(version,f,order++));}}
    private MaterialView replay(UUID userId,String operation,String key,String fp){return idempotency.findByUserIdAndOperationAndIdempotencyKey(userId,operation,key).map(r->{if(!r.getPayloadFingerprint().equals(fp))throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","Idempotency-Key was already used with a different request");Material m=material(r.getMaterialId());MaterialVersion v=versions.findByMaterial_IdAndVersionNumber(m.getId(),r.getVersionNumber()).orElseThrow();return view(m,v,userId,!v.getId().equals(m.getCurrentVersionId()));}).orElse(null);}
    private MaterialView view(Material m,MaterialVersion v,UUID viewer,boolean historical){return new MaterialView(m.getId(),m.getProject().getId(),m.getOwner().getId(),m.getType(),m.getMaterialCreatedTime(),m.getCurrentVersionId(),!historical&&m.getOwner().getId().equals(viewer),historical,versionView(v));}
    private VersionView versionView(MaterialVersion v){VersionContentView content=v.getContentKind()==VersionContentKind.TEXT?new TextVersionContentView(v.getTextBody()):new FileVersionContentView(v.getFileTextContent(),versionFiles.findByVersion_IdOrderByDisplayOrder(v.getId()).stream().map(VersionFile::getFile).map(this::fileView).toList());return new VersionView(v.getId(),v.getVersionNumber(),v.getCreatedBy().getId(),v.getVersionCreatedTime(),new VersionSnapshotView(v.getTitle(),v.getEventTime(),v.getLocation(),v.getManualTags(),content));}
    private FileAssetView fileView(FileAsset f){
        ImageAnalysisView analysis=imageAnalyses.findByFile_Id(f.getId()).map(this::analysisView).orElse(null);
        return new FileAssetView(f.getId(),f.getUploader().getId(),f.getOriginalFilename(),f.getMimeType(),f.getSizeBytes(),f.getChecksumSha256(),f.getFileCreatedTime(),analysis);
    }
    private ImageAnalysisView analysisView(ImageAnalysis a){
        return new ImageAnalysisView(a.getStatus(),a.getAiDescription(),a.getAiTags(),a.getGeneratedTime());
    }
    private ReportingProject project(UUID id){return projects.findById(id).orElseThrow(()->missing("PROJECT_NOT_FOUND","Project was not found"));}
    private Material material(UUID id){return materials.findById(id).orElseThrow(()->missing("MATERIAL_NOT_FOUND","Material was not found"));}
    private ApiException missing(String code,String message){return new ApiException(HttpStatus.NOT_FOUND,code,message);} private ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","You do not have permission for this action");}
    private void invalidFiles(){throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_FILE_SET","File set is invalid");}
    private List<String> tags(List<String> in){return in==null?List.of():in.stream().filter(Objects::nonNull).map(String::trim).filter(s->!s.isEmpty()).distinct().toList();}
    private String clean(String value){return value.trim();} private String nullableClean(String value){return value==null||value.isBlank()?null:value.trim();}
    private String fingerprint(Object... values){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
