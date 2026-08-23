package com.example.newsmaterial.analysis;

public interface ImageAnalysisProvider {
    ImageAnalysisResult analyze(byte[] imageBytes, String mimeType);

    default ImageAnalysisResult analyze(byte[] imageBytes, String mimeType, String originalFilename) {
        return analyze(imageBytes, mimeType);
    }
}
