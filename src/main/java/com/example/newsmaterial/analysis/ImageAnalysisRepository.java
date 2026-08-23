package com.example.newsmaterial.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImageAnalysisRepository extends JpaRepository<ImageAnalysis, UUID> {
    Optional<ImageAnalysis> findByFile_Id(UUID fileId);
    long countByFile_Id(UUID fileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ImageAnalysis a where a.id = :id")
    Optional<ImageAnalysis> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ImageAnalysis a join fetch a.file where " +
            "(a.status = :pending and (a.nextAttemptTime is null or a.nextAttemptTime <= :now)) or " +
            "(a.status = :processing and a.leaseExpiresAt is not null and a.leaseExpiresAt <= :now) " +
            "order by case when a.status = :pending then a.nextAttemptTime else a.leaseExpiresAt end, a.id")
    List<ImageAnalysis> findEligible(ImageAnalysisStatus pending, ImageAnalysisStatus processing,
                                     Instant now, Pageable pageable);
}
