package com.example.newsmaterial.file;

import com.example.newsmaterial.material.domain.Material;
import com.example.newsmaterial.security.AppUser;
import com.example.newsmaterial.upload.UploadIntent;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="file_assets")
public class FileAsset {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="material_id",nullable=false,updatable=false) private Material material;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="uploader_id",nullable=false,updatable=false) private AppUser uploader;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="upload_intent_id",nullable=false,unique=true,updatable=false) private UploadIntent uploadIntent;
    @Column(name="object_key",nullable=false,unique=true,updatable=false,length=600) private String objectKey;
    @Column(name="original_filename",nullable=false,updatable=false,length=255) private String originalFilename;
    @Column(name="mime_type",nullable=false,updatable=false,length=160) private String mimeType;
    @Column(name="size_bytes",nullable=false,updatable=false) private long sizeBytes;
    @Column(name="checksum_sha256",nullable=false,updatable=false,length=64) private String checksumSha256;
    @Column(name="file_created_time",nullable=false,updatable=false) private Instant fileCreatedTime;
    protected FileAsset(){}
    public FileAsset(UUID id,Material material,AppUser uploader,UploadIntent intent,Instant created){this.id=id;this.material=material;this.uploader=uploader;this.uploadIntent=intent;this.objectKey=intent.getObjectKey();this.originalFilename=intent.getOriginalFilename();this.mimeType=intent.getMimeType();this.sizeBytes=intent.getSizeBytes();this.checksumSha256=intent.getChecksumSha256();this.fileCreatedTime=created;}
    public UUID getId(){return id;} public Material getMaterial(){return material;} public AppUser getUploader(){return uploader;}
    public String getObjectKey(){return objectKey;} public String getOriginalFilename(){return originalFilename;} public String getMimeType(){return mimeType;}
    public long getSizeBytes(){return sizeBytes;} public String getChecksumSha256(){return checksumSha256;} public Instant getFileCreatedTime(){return fileCreatedTime;}
}
