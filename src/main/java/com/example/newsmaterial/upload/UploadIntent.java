package com.example.newsmaterial.upload;

import com.example.newsmaterial.security.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_intents")
public class UploadIntent {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, updatable = false) private AppUser user;
    @Column(name = "object_key", nullable = false, unique = true, updatable = false, length = 600) private String objectKey;
    @Column(name = "original_filename", nullable = false, updatable = false, length = 255) private String originalFilename;
    @Column(name = "mime_type", nullable = false, updatable = false, length = 160) private String mimeType;
    @Column(name = "size_bytes", nullable = false, updatable = false) private long sizeBytes;
    @Column(name = "checksum_sha256", nullable = false, updatable = false, length = 64) private String checksumSha256;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private UploadIntentStatus status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "consumed_at") private Instant consumedAt;

    protected UploadIntent() {}
    public UploadIntent(UUID id, AppUser user, String objectKey, String originalFilename, String mimeType, long sizeBytes,
                        String checksumSha256, Instant expiresAt, Instant createdAt) {
        this.id=id; this.user=user; this.objectKey=objectKey; this.originalFilename=originalFilename; this.mimeType=mimeType;
        this.sizeBytes=sizeBytes; this.checksumSha256=checksumSha256; this.status=UploadIntentStatus.PENDING;
        this.expiresAt=expiresAt; this.createdAt=createdAt;
    }
    public UUID getId(){return id;} public AppUser getUser(){return user;} public String getObjectKey(){return objectKey;}
    public String getOriginalFilename(){return originalFilename;} public String getMimeType(){return mimeType;}
    public long getSizeBytes(){return sizeBytes;} public String getChecksumSha256(){return checksumSha256;}
    public UploadIntentStatus getStatus(){return status;} public Instant getExpiresAt(){return expiresAt;}
    public void consume(Instant at){status=UploadIntentStatus.CONSUMED; consumedAt=at;}
}
