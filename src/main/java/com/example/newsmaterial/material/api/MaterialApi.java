package com.example.newsmaterial.material.api;

import com.example.newsmaterial.material.domain.MaterialType;
import com.example.newsmaterial.material.service.MaterialService;
import com.example.newsmaterial.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/materials")
public class MaterialApi {
    private final MaterialService materials;

    public MaterialApi(MaterialService materials) {
        this.materials = materials;
    }

    @PostMapping("/text")
    ResponseEntity<MaterialView> createText(@AuthenticationPrincipal AuthenticatedUser user, @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
                                            @Valid @RequestBody TextMaterialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(materials.createText(user.id(), idempotencyKey, request));
    }

    @GetMapping("/{id}")
    MaterialView current(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return materials.current(id, user.id());
    }

    @GetMapping("/{id}/versions")
    List<VersionView> history(@PathVariable UUID id) {
        return materials.history(id);
    }

    @GetMapping("/{id}/versions/{number}")
    MaterialView historical(@PathVariable UUID id, @PathVariable int number, @AuthenticationPrincipal AuthenticatedUser user) {
        return materials.historical(id, number, user.id());
    }

    @PostMapping("/{id}/versions")
    ResponseEntity<MaterialView> createVersion(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user,
                                               @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
                                               @Valid @RequestBody TextVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(materials.createVersion(id, user.id(), idempotencyKey, request));
    }

    public record TextMaterialRequest(@NotNull UUID projectId, @NotBlank String title, Instant eventTime, String location,
                                      List<String> manualTags, @NotBlank String textBody) {
    }

    public record TextVersionRequest(@NotNull Integer expectedCurrentVersion, @NotBlank String title, Instant eventTime,
                                     String location, List<String> manualTags, @NotBlank String textBody) {
    }

    public record MaterialView(UUID id, UUID projectId, UUID ownerId, MaterialType type, Instant materialCreatedTime,
                               UUID currentVersionId, boolean canEdit, boolean historical, VersionView version) {
    }

    public record VersionView(UUID id, int versionNumber, UUID createdBy, Instant versionCreatedTime, VersionSnapshotView snapshot) {
    }

    public record VersionSnapshotView(String title, Instant eventTime, String location, List<String> manualTags,
                                      VersionContentView content) {
    }

    public sealed interface VersionContentView permits TextVersionContentView {
    }

    public record TextVersionContentView(String kind, String textBody) implements VersionContentView {
        public TextVersionContentView(String textBody) {
            this("TEXT", textBody);
        }
    }
}
