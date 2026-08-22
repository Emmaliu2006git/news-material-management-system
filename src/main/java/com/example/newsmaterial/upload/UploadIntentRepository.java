package com.example.newsmaterial.upload;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface UploadIntentRepository extends JpaRepository<UploadIntent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select intent from UploadIntent intent where intent.id=:id")
    Optional<UploadIntent> findByIdForUpdate(UUID id);
}
