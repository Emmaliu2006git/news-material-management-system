package com.example.newsmaterial.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, String title, UUID creatorId, String creatorUsername,
                              Instant createdAt, Instant updatedAt) {
    public static ProjectResponse from(ReportingProject project) {
        return new ProjectResponse(project.getId(), project.getTitle(), project.getCreatedBy().getId(),
                project.getCreatedBy().getUsername(), project.getCreatedAt(), project.getUpdatedAt());
    }
}
