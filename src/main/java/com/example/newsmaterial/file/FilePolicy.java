package com.example.newsmaterial.file;

import com.example.newsmaterial.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class FilePolicy {
    private final int maxFiles;
    private final long maxFileBytes;
    private final long maxTotalBytes;

    public FilePolicy(@Value("${app.upload.max-files:10}") int maxFiles,
                      @Value("${app.upload.max-file-bytes:52428800}") long maxFileBytes,
                      @Value("${app.upload.max-total-bytes:209715200}") long maxTotalBytes) {
        if (maxFiles < 1 || maxFileBytes < 1 || maxTotalBytes < maxFileBytes) {
            throw new IllegalArgumentException("Upload limits must be positive and total must allow one maximum-size file");
        }
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileBytes;
        this.maxTotalBytes = maxTotalBytes;
    }

    public void validateFileCount(int count) {
        if (count > maxFiles) {
            limit();
        }
        if (count < 1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FILE_SET", "At least one file is required");
        }
    }

    public void validateSnapshot(Collection<Long> sizes) {
        if (sizes == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FILE_SET", "At least one file is required");
        }
        validateFileCount(sizes.size());
        long total = 0;
        try {
            for (Long size : sizes) {
                if (size == null || size <= 0 || size > maxFileBytes) {
                    limit();
                }
                total = Math.addExact(total, size);
                if (total > maxTotalBytes) {
                    limit();
                }
            }
        } catch (ArithmeticException overflow) {
            limit();
        }
    }

    public int maxFiles() { return maxFiles; }
    public long maxFileBytes() { return maxFileBytes; }
    public long maxTotalBytes() { return maxTotalBytes; }

    private void limit() {
        throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_LIMIT_EXCEEDED", "Upload limits were exceeded");
    }
}
