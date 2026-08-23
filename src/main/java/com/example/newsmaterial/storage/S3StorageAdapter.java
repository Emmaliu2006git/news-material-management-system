package com.example.newsmaterial.storage;

import com.example.newsmaterial.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import java.time.*;
import java.util.*;

@Component @Profile("!local")
public class S3StorageAdapter implements StoragePort {
    private final S3Client client; private final S3Presigner presigner; private final String bucket;
    public S3StorageAdapter(S3Client client,S3Presigner presigner,@Value("${app.storage.bucket}") String bucket){this.client=client;this.presigner=presigner;this.bucket=bucket;}
    @Override public UploadTarget createUploadTarget(UUID intentId,String key,String mime,long size,String sha,Instant expires){
        String checksum=Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha));
        PutObjectRequest put=PutObjectRequest.builder().bucket(bucket).key(key).contentType(mime).contentLength(size).checksumSHA256(checksum).build();
        PresignedPutObjectRequest signed=presigner.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(duration(expires)).putObjectRequest(put).build());
        return new UploadTarget(signed.url().toString(),Map.of("Content-Type",mime,"x-amz-checksum-sha256",checksum));
    }
    @Override public StoredObject inspect(String key){
        try { HeadObjectResponse head=client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).checksumMode(ChecksumMode.ENABLED).build());
            String sha=head.checksumSHA256()==null?null:HexFormat.of().formatHex(Base64.getDecoder().decode(head.checksumSHA256()));
            return new StoredObject(head.contentLength(),head.contentType(),sha);
        } catch (NoSuchKeyException e){throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"UPLOAD_NOT_VERIFIED","Uploaded object was not found");}
        catch (S3Exception e){if(e.statusCode()==404)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"UPLOAD_NOT_VERIFIED","Uploaded object was not found");throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","Storage verification failed");}
    }
    @Override public String createDownloadUrl(UUID fileId,String key,String filename,String mime,Instant expires){
        GetObjectRequest get=GetObjectRequest.builder().bucket(bucket).key(key).responseContentType(mime).responseContentDisposition("attachment; filename=\""+filename.replace("\"","")+"\"").build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(duration(expires)).getObjectRequest(get).build()).url().toString();
    }
    @Override public byte[] readBytes(String key) {
        try { return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray(); }
        catch (S3Exception e) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","Storage could not be read"); }
    }
    private Duration duration(Instant expires){Duration d=Duration.between(Instant.now(),expires);return d.isNegative()?Duration.ofSeconds(1):d;}
}
