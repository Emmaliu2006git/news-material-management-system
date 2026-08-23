package com.example.newsmaterial.analysis;

public interface ImageAnalysisProvider {
    ImageAnalysisResult analyze(byte[] imageBytes, String mimeType);
}
