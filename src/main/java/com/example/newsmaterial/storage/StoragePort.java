package com.example.newsmaterial.storage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface StoragePort {
    UploadTarget createUploadTarget(UUID intentId, String objectKey, String mimeType, long sizeBytes, String checksumSha256, Instant expiresAt);
    StoredObject inspect(String objectKey);
    String createDownloadUrl(UUID fileId, String objectKey, String filename, String mimeType, Instant expiresAt);
    byte[] readBytes(String objectKey);
    record UploadTarget(String uploadUrl, Map<String,String> requiredHeaders) {}
    record StoredObject(long sizeBytes, String mimeType, String checksumSha256) {}
}
