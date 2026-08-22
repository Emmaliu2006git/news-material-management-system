package com.example.newsmaterial.material.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = @UniqueConstraint(name = "uq_idempotency_user_operation_key", columnNames = {"user_id", "operation_name", "idempotency_key"}))
public class IdempotencyRecord {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(name = "operation_name", nullable = false, length = 40, updatable = false) private String operation;
    @Column(name = "idempotency_key", nullable = false, length = 255, updatable = false) private String idempotencyKey;
    @Column(name = "payload_fingerprint", nullable = false, length = 128, updatable = false) private String payloadFingerprint;
    @Column(name = "material_id", nullable = false, updatable = false) private UUID materialId;
    @Column(name = "version_number", nullable = false, updatable = false) private int versionNumber;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(UUID id, UUID userId, String operation, String idempotencyKey, String payloadFingerprint,
                             UUID materialId, int versionNumber, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.payloadFingerprint = payloadFingerprint;
        this.materialId = materialId;
        this.versionNumber = versionNumber;
        this.createdAt = createdAt;
    }

    public String getPayloadFingerprint() { return payloadFingerprint; }
    public UUID getMaterialId() { return materialId; }
    public int getVersionNumber() { return versionNumber; }
}
