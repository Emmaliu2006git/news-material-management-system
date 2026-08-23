package com.example.newsmaterial.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.analysis.provider", havingValue = "local", matchIfMissing = true)
public class LocalImageAnalysisProvider implements ImageAnalysisProvider {
    @Override
    public ImageAnalysisResult analyze(byte[] imageBytes, String mimeType) {
        try {
            String fingerprint = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(imageBytes)).substring(0, 12);
            String subtype = mimeType.substring(mimeType.indexOf('/') + 1).toLowerCase();
            return new ImageAnalysisResult(
                    "Local demo analysis for image " + fingerprint,
                    List.of("image", subtype, "demo-analysis"));
        } catch (Exception exception) {
            throw new IllegalStateException("Local image analysis failed", exception);
        }
    }

    @Override
    public ImageAnalysisResult analyze(byte[] imageBytes, String mimeType, String originalFilename) {
        if (originalFilename != null && originalFilename.toLowerCase().startsWith("fail-ai-")) {
            throw new IllegalStateException("Local demo failure requested by filename");
        }
        return analyze(imageBytes, mimeType);
    }
}
