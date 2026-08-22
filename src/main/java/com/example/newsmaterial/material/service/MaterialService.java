package com.example.newsmaterial.material.service;

import com.example.newsmaterial.common.api.ApiException;
import com.example.newsmaterial.material.api.MaterialApi.MaterialView;
import com.example.newsmaterial.material.api.MaterialApi.TextMaterialRequest;
import com.example.newsmaterial.material.api.MaterialApi.TextVersionContentView;
import com.example.newsmaterial.material.api.MaterialApi.TextVersionRequest;
import com.example.newsmaterial.material.api.MaterialApi.VersionSnapshotView;
import com.example.newsmaterial.material.api.MaterialApi.VersionView;
import com.example.newsmaterial.material.domain.Material;
import com.example.newsmaterial.material.domain.MaterialType;
import com.example.newsmaterial.material.domain.MaterialVersion;
import com.example.newsmaterial.material.persistence.IdempotencyRecord;
import com.example.newsmaterial.material.persistence.IdempotencyRecordRepository;
import com.example.newsmaterial.material.persistence.MaterialRepository;
import com.example.newsmaterial.material.persistence.MaterialVersionRepository;
import com.example.newsmaterial.project.ProjectRepository;
import com.example.newsmaterial.project.ReportingProject;
import com.example.newsmaterial.security.AppUser;
import com.example.newsmaterial.security.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class MaterialService {
    private static final String CREATE_TEXT = "CREATE_TEXT";
    private static final String CREATE_VERSION = "CREATE_VERSION";
    private final MaterialRepository materials;
    private final MaterialVersionRepository versions;
    private final IdempotencyRecordRepository idempotency;
    private final ProjectRepository projects;
    private final UserRepository users;

    public MaterialService(MaterialRepository materials, MaterialVersionRepository versions, IdempotencyRecordRepository idempotency,
                           ProjectRepository projects, UserRepository users) {
        this.materials = materials;
        this.versions = versions;
        this.idempotency = idempotency;
        this.projects = projects;
        this.users = users;
    }

    @Transactional
    public MaterialView createText(UUID userId, String key, TextMaterialRequest request) {
        String fingerprint = fingerprint(request.projectId(), request.title(), request.eventTime(), request.location(), request.manualTags(), request.textBody());
        MaterialView replay = replay(userId, CREATE_TEXT, key, fingerprint);
        if (replay != null) return replay;
        ReportingProject project = projects.findById(request.projectId()).orElseThrow(() -> missing("PROJECT_NOT_FOUND", "Project was not found"));
        AppUser owner = users.findById(userId).orElseThrow();
        Instant now = Instant.now();
        UUID firstVersionId = UUID.randomUUID();
        Material material = new Material(UUID.randomUUID(), project, owner, MaterialType.TEXT, now);
        material.setCurrentVersionId(firstVersionId);
        materials.save(material);
        MaterialVersion first = versions.save(new MaterialVersion(firstVersionId, material, 1, owner, now,
                clean(request.title()), request.eventTime(), nullableClean(request.location()), tags(request.manualTags()), clean(request.textBody())));
        material.setCurrentVersionId(first.getId());
        idempotency.save(new IdempotencyRecord(UUID.randomUUID(), userId, CREATE_TEXT, key, fingerprint, material.getId(), 1, now));
        return view(material, first, userId, false);
    }

    @Transactional(readOnly = true)
    public MaterialView current(UUID materialId, UUID userId) {
        Material material = material(materialId);
        MaterialVersion current = versions.findById(material.getCurrentVersionId()).orElseThrow();
        return view(material, current, userId, false);
    }

    @Transactional(readOnly = true)
    public List<VersionView> history(UUID materialId) {
        material(materialId);
        return versions.findByMaterial_IdOrderByVersionNumberAsc(materialId).stream().map(this::versionView).toList();
    }

    @Transactional(readOnly = true)
    public MaterialView historical(UUID materialId, int number, UUID userId) {
        Material material = material(materialId);
        MaterialVersion version = versions.findByMaterial_IdAndVersionNumber(materialId, number)
                .orElseThrow(() -> missing("VERSION_NOT_FOUND", "Version was not found"));
        return view(material, version, userId, !version.getId().equals(material.getCurrentVersionId()));
    }

    @Transactional
    public MaterialView createVersion(UUID materialId, UUID userId, String key, TextVersionRequest request) {
        String fingerprint = fingerprint(materialId, request.expectedCurrentVersion(), request.title(), request.eventTime(), request.location(), request.manualTags(), request.textBody());
        MaterialView replay = replay(userId, CREATE_VERSION, key, fingerprint);
        if (replay != null) return replay;
        Material material = materials.findByIdForUpdate(materialId).orElseThrow(() -> missing("MATERIAL_NOT_FOUND", "Material was not found"));
        if (!material.getOwner().getId().equals(userId)) throw forbidden();
        if (material.getType() != MaterialType.TEXT) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MATERIAL_TYPE_MISMATCH", "A TEXT version is required");
        MaterialVersion current = versions.findById(material.getCurrentVersionId()).orElseThrow();
        if (request.expectedCurrentVersion() != current.getVersionNumber()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The material has a newer version");
        }
        AppUser owner = users.findById(userId).orElseThrow();
        Instant now = Instant.now();
        MaterialVersion next = versions.save(new MaterialVersion(UUID.randomUUID(), material, current.getVersionNumber() + 1, owner, now,
                clean(request.title()), request.eventTime(), nullableClean(request.location()), tags(request.manualTags()), clean(request.textBody())));
        material.setCurrentVersionId(next.getId());
        idempotency.save(new IdempotencyRecord(UUID.randomUUID(), userId, CREATE_VERSION, key, fingerprint, materialId, next.getVersionNumber(), now));
        return view(material, next, userId, false);
    }

    private MaterialView replay(UUID userId, String operation, String key, String fingerprint) {
        return idempotency.findByUserIdAndOperationAndIdempotencyKey(userId, operation, key).map(record -> {
            if (!record.getPayloadFingerprint().equals(fingerprint)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used with a different request");
            }
            Material material = material(record.getMaterialId());
            MaterialVersion version = versions.findByMaterial_IdAndVersionNumber(material.getId(), record.getVersionNumber()).orElseThrow();
            return view(material, version, userId, !version.getId().equals(material.getCurrentVersionId()));
        }).orElse(null);
    }

    private Material material(UUID id) {
        return materials.findById(id).orElseThrow(() -> missing("MATERIAL_NOT_FOUND", "Material was not found"));
    }

    private ApiException missing(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission for this action");
    }

    private MaterialView view(Material material, MaterialVersion version, UUID viewerId, boolean historical) {
        return new MaterialView(material.getId(), material.getProject().getId(), material.getOwner().getId(), material.getType(),
                material.getMaterialCreatedTime(), material.getCurrentVersionId(), !historical && material.getOwner().getId().equals(viewerId),
                historical, versionView(version));
    }

    private VersionView versionView(MaterialVersion version) {
        return new VersionView(version.getId(), version.getVersionNumber(), version.getCreatedBy().getId(), version.getVersionCreatedTime(),
                new VersionSnapshotView(version.getTitle(), version.getEventTime(), version.getLocation(), version.getManualTags(),
                        new TextVersionContentView(version.getTextBody())));
    }

    private List<String> tags(List<String> input) {
        return input == null ? List.of() : input.stream().filter(Objects::nonNull).map(String::trim).filter(tag -> !tag.isEmpty()).distinct().toList();
    }

    private String clean(String value) {
        return value.trim();
    }

    private String nullableClean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String fingerprint(Object... values) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(java.util.Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
