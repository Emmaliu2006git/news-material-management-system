package com.example.newsmaterial.material.domain;

import com.example.newsmaterial.security.AppUser;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "material_versions")
public class MaterialVersion {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "material_id", nullable = false, updatable = false) private Material material;
    @Column(name = "version_number", nullable = false, updatable = false) private int versionNumber;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false, updatable = false) private AppUser createdBy;
    @Column(name = "version_created_time", nullable = false, updatable = false) private Instant versionCreatedTime;
    @Column(nullable = false, length = 240, updatable = false) private String title;
    @Column(name = "event_time", updatable = false) private Instant eventTime;
    @Column(length = 240, updatable = false) private String location;
    @ElementCollection @CollectionTable(name = "material_version_tags", joinColumns = @JoinColumn(name = "version_id"))
    @OrderColumn(name = "position") @Column(name = "tag_value", nullable = false, length = 80)
    private List<String> manualTags = new ArrayList<>();
    @Enumerated(EnumType.STRING) @Column(name = "content_kind", nullable = false, length = 16, updatable = false)
    private VersionContentKind contentKind;
    @Column(name = "text_body", columnDefinition = "text", updatable = false) private String textBody;
    @Column(name = "file_text_content", columnDefinition = "text", updatable = false) private String fileTextContent;

    protected MaterialVersion() {
    }

    public MaterialVersion(UUID id, Material material, int versionNumber, AppUser createdBy, Instant versionCreatedTime,
                           String title, Instant eventTime, String location, List<String> manualTags, String textBody) {
        this.id = id;
        this.material = material;
        this.versionNumber = versionNumber;
        this.createdBy = createdBy;
        this.versionCreatedTime = versionCreatedTime;
        this.title = title;
        this.eventTime = eventTime;
        this.location = location;
        this.manualTags = new ArrayList<>(manualTags);
        this.contentKind = VersionContentKind.TEXT;
        this.textBody = textBody;
    }

    public static MaterialVersion file(UUID id, Material material, int versionNumber, AppUser createdBy, Instant createdAt,
                                       String title, Instant eventTime, String location, List<String> tags, String textContent) {
        MaterialVersion v = new MaterialVersion();
        v.id=id; v.material=material; v.versionNumber=versionNumber; v.createdBy=createdBy; v.versionCreatedTime=createdAt;
        v.title=title; v.eventTime=eventTime; v.location=location; v.manualTags=new ArrayList<>(tags);
        v.contentKind=VersionContentKind.FILE; v.fileTextContent=textContent;
        return v;
    }

    public UUID getId() { return id; }
    public Material getMaterial() { return material; }
    public int getVersionNumber() { return versionNumber; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getVersionCreatedTime() { return versionCreatedTime; }
    public String getTitle() { return title; }
    public Instant getEventTime() { return eventTime; }
    public String getLocation() { return location; }
    public List<String> getManualTags() { return List.copyOf(manualTags); }
    public VersionContentKind getContentKind() { return contentKind; }
    public String getTextBody() { return textBody; }
    public String getFileTextContent() { return fileTextContent; }
}
