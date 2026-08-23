package com.example.newsmaterial.analysis;

import com.example.newsmaterial.file.FileAsset;
import com.example.newsmaterial.common.api.ApiException;
import com.example.newsmaterial.file.FileAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ImageAnalysisService {
    private final ImageAnalysisRepository analyses;
    private final FileAssetRepository files;

    public ImageAnalysisService(ImageAnalysisRepository analyses, FileAssetRepository files) {
        this.analyses = analyses;
        this.files = files;
    }

    public void ensurePending(FileAsset file, Instant now) {
        if (!file.getMimeType().toLowerCase().startsWith("image/")) {
            return;
        }
        if (analyses.findByFile_Id(file.getId()).isEmpty()) {
            analyses.save(ImageAnalysis.pending(UUID.randomUUID(), file, now));
        }
    }
    @Transactional
    public RetryResult retry(UUID fileId, UUID userId, Instant now) {
        FileAsset file = files.findById(fileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File was not found"));
        if (!file.getMaterial().getOwner().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission for this action");
        }
        ImageAnalysis analysis = analyses.findByFile_Id(fileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "Image analysis was not found"));
        if (analysis.getStatus() != ImageAnalysisStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "ANALYSIS_NOT_FAILED", "Only failed analysis can be retried");
        }
        analysis.retry(now);
        return new RetryResult(analysis.getStatus(), analysis.getAttemptCount(), analysis.getNextAttemptTime());
    }

    public record RetryResult(ImageAnalysisStatus status, int attemptCount, Instant nextAttemptTime) {
    }
}
