package com.example.newsmaterial.search;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "search_documents")
public class SearchDocument {
    @Id
    private UUID id;

    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @Column(name = "current_version_id", nullable = false, updatable = false)
    private UUID currentVersionId;

    @Column(name = "file_id", updatable = false)
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16, updatable = false)
    private SearchSourceType sourceType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    protected SearchDocument() {
    }

    public SearchDocument(UUID id, UUID materialId, UUID currentVersionId, UUID fileId,
                          SearchSourceType sourceType, String content) {
        this.id = id;
        this.materialId = materialId;
        this.currentVersionId = currentVersionId;
        this.fileId = fileId;
        this.sourceType = sourceType;
        this.content = content;
    }

    public void replaceContent(String content) {
        this.content = content;
    }

    public UUID getId() { return id; }
    public UUID getMaterialId() { return materialId; }
    public UUID getCurrentVersionId() { return currentVersionId; }
    public UUID getFileId() { return fileId; }
    public SearchSourceType getSourceType() { return sourceType; }
    public String getContent() { return content; }
}
