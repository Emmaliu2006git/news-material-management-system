package com.example.newsmaterial.search;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SearchDocumentRepository extends JpaRepository<SearchDocument, UUID> {
    Optional<SearchDocument> findByMaterialIdAndCurrentVersionIdAndFileIdIsNullAndSourceType(
            UUID materialId, UUID currentVersionId, SearchSourceType sourceType);

    Optional<SearchDocument> findByMaterialIdAndCurrentVersionIdAndFileIdAndSourceType(
            UUID materialId, UUID currentVersionId, UUID fileId, SearchSourceType sourceType);

    @Query(value = "select d.* from search_documents d join materials m on m.id = d.material_id " +
            "and m.current_version_id = d.current_version_id " +
            "where m.project_id = :projectId and d.content ilike concat('%', :query, '%') " +
            "escape '\\'", nativeQuery = true)
    List<SearchDocument> findCurrentMatches(UUID projectId, String query);
}
