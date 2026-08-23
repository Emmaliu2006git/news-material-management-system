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
    @Query("select a from ImageAnalysis a join fetch a.file where a.status = :status and " +
            "(a.nextAttemptTime is null or a.nextAttemptTime <= :now) order by a.nextAttemptTime, a.id")
    List<ImageAnalysis> findEligible(ImageAnalysisStatus status, Instant now, Pageable pageable);
}
