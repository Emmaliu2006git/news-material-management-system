package com.example.newsmaterial.file;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
@Embeddable public record VersionFileId(UUID versionId,UUID fileId) implements Serializable{}
