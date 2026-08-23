package com.example.newsmaterial.material.api;

import com.example.newsmaterial.analysis.ImageAnalysisStatus;
import com.example.newsmaterial.material.domain.MaterialType;
import com.example.newsmaterial.material.service.MaterialService;
import com.example.newsmaterial.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/materials")
public class MaterialApi {
    private final MaterialService materials;
    public MaterialApi(MaterialService materials) { this.materials = materials; }
    @PostMapping("/text") ResponseEntity<MaterialView> createText(@AuthenticationPrincipal AuthenticatedUser user, @RequestHeader("Idempotency-Key") @NotBlank String key, @Valid @RequestBody TextMaterialRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(materials.createText(user.id(), key, request)); }
    @PostMapping("/file") ResponseEntity<MaterialView> createFile(@AuthenticationPrincipal AuthenticatedUser user, @RequestHeader("Idempotency-Key") @NotBlank String key, @Valid @RequestBody FileMaterialRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(materials.createFile(user.id(), key, request)); }
    @GetMapping("/{id}") MaterialView current(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) { return materials.current(id, user.id()); }
    @GetMapping("/{id}/versions") List<VersionView> history(@PathVariable UUID id) { return materials.history(id); }
    @GetMapping("/{id}/versions/{number}") MaterialView historical(@PathVariable UUID id, @PathVariable int number, @AuthenticationPrincipal AuthenticatedUser user) { return materials.historical(id, number, user.id()); }
    @PostMapping("/{id}/versions") ResponseEntity<MaterialView> createVersion(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user, @RequestHeader("Idempotency-Key") @NotBlank String key, @Valid @RequestBody MaterialVersionRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(materials.createVersion(id, user.id(), key, request)); }

    public record TextMaterialRequest(@NotNull UUID projectId, @NotBlank String title, Instant eventTime, String location, List<String> manualTags, @NotBlank String textBody) {}
    public record FileMaterialRequest(@NotNull UUID projectId, @NotBlank String title, Instant eventTime, String location, List<String> manualTags, String textContent, List<UUID> uploadIntentIds) {}
    public record TextVersionRequest(@NotNull Integer expectedCurrentVersion, @NotBlank String title, Instant eventTime, String location, List<String> manualTags, @NotBlank String textBody) {}
    public enum FileSelectionKind { EXISTING, UPLOAD }
    public record FileSelectionRequest(FileSelectionKind kind, UUID fileId, UUID uploadIntentId) {}
    public record MaterialVersionRequest(@NotNull Integer expectedCurrentVersion, @NotBlank String title, Instant eventTime, String location, List<String> manualTags, String textBody, String textContent, List<UUID> fileIds, List<UUID> uploadIntentIds, List<FileSelectionRequest> files) {}
    public record MaterialView(UUID id, UUID projectId, String projectTitle, UUID ownerId, String ownerUsername, MaterialType type, Instant materialCreatedTime, UUID currentVersionId, boolean canEdit, boolean historical, VersionView version) {}
    public record VersionView(UUID id, int versionNumber, UUID createdBy, String creatorUsername, Instant versionCreatedTime, VersionSnapshotView snapshot) {}
    public record VersionSnapshotView(String title, Instant eventTime, String location, List<String> manualTags, VersionContentView content) {}
    public sealed interface VersionContentView permits TextVersionContentView, FileVersionContentView {}
    public record TextVersionContentView(String kind, String textBody) implements VersionContentView { public TextVersionContentView(String textBody){this("TEXT",textBody);} }
    public record FileVersionContentView(String kind, String textContent, List<FileAssetView> files) implements VersionContentView { public FileVersionContentView(String textContent,List<FileAssetView> files){this("FILE",textContent,files);} }
    public record FileAssetView(UUID id, UUID uploaderId, String uploaderUsername, String originalFilename, String mimeType, long sizeBytes, String checksumSha256, Instant fileCreatedTime, ImageAnalysisView imageAnalysis) {}
    public record ImageAnalysisView(ImageAnalysisStatus status, String aiDescription, List<String> aiTags, Instant generatedTime) {}
}
