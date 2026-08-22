package com.example.newsmaterial.material.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByUserIdAndOperationAndIdempotencyKey(UUID userId, String operation, String idempotencyKey);
}
