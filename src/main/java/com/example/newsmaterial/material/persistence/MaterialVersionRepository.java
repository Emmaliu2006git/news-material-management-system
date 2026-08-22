package com.example.newsmaterial.material.persistence;

import com.example.newsmaterial.material.domain.MaterialVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialVersionRepository extends JpaRepository<MaterialVersion, UUID> {
    List<MaterialVersion> findByMaterial_IdOrderByVersionNumberAsc(UUID materialId);
    Optional<MaterialVersion> findByMaterial_IdAndVersionNumber(UUID materialId, int versionNumber);
}
