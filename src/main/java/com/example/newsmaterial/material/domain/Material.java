package com.example.newsmaterial.material.domain;

import com.example.newsmaterial.project.ReportingProject;
import com.example.newsmaterial.security.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "materials")
public class Material {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private ReportingProject project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_id", nullable = false) private AppUser owner;
    @Enumerated(EnumType.STRING) @Column(name = "material_type", nullable = false, length = 16) private MaterialType type;
    @Column(name = "material_created_time", nullable = false, updatable = false) private Instant materialCreatedTime;
    @Column(name = "current_version_id", nullable = false) private UUID currentVersionId;

    protected Material() {
    }

    public Material(UUID id, ReportingProject project, AppUser owner, MaterialType type, Instant materialCreatedTime) {
        this.id = id;
        this.project = project;
        this.owner = owner;
        this.type = type;
        this.materialCreatedTime = materialCreatedTime;
    }

    public UUID getId() { return id; }
    public ReportingProject getProject() { return project; }
    public AppUser getOwner() { return owner; }
    public MaterialType getType() { return type; }
    public Instant getMaterialCreatedTime() { return materialCreatedTime; }
    public UUID getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(UUID currentVersionId) { this.currentVersionId = currentVersionId; }
}
