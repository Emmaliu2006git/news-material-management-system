package com.example.newsmaterial.analysis;

import java.util.List;
import java.util.Objects;

public record ImageAnalysisResult(String description, List<String> tags) {
    public ImageAnalysisResult {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("AI description is required");
        }
        description = description.trim();
        tags = tags == null ? List.of() : tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .limit(20)
                .toList();
        if (description.length() > 4_000 || tags.stream().anyMatch(tag -> tag.length() > 80)) {
            throw new IllegalArgumentException("AI result exceeds limits");
        }
    }
}
