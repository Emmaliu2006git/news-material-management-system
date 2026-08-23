package com.example.newsmaterial.search;

import com.example.newsmaterial.common.api.ApiException;
import com.example.newsmaterial.material.domain.Material;
import com.example.newsmaterial.material.domain.MaterialType;
import com.example.newsmaterial.material.domain.MaterialVersion;
import com.example.newsmaterial.material.persistence.MaterialRepository;
import com.example.newsmaterial.material.persistence.MaterialVersionRepository;
import com.example.newsmaterial.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchService {
    private final ProjectRepository projects;
    private final MaterialRepository materials;
    private final MaterialVersionRepository versions;
    private final SearchDocumentRepository documents;

    public SearchService(ProjectRepository projects, MaterialRepository materials,
                         MaterialVersionRepository versions, SearchDocumentRepository documents) {
        this.projects = projects;
        this.materials = materials;
        this.versions = versions;
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(UUID projectId, SearchFilters filters) {
        if (!projects.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project was not found");
        }
        String query = escapeLike(normalize(filters.q()));
        Map<UUID, UUID> matches = new LinkedHashMap<>();
        if (query == null) {
            materials.findByProject_Id(projectId).forEach(material -> matches.put(material.getId(), null));
        } else {
            for (SearchDocument document : documents.findCurrentMatches(projectId, query)) {
                UUID matchedFile = document.getSourceType() == SearchSourceType.IMAGE_AI
                        ? document.getFileId() : null;
                UUID materialId = document.getMaterialId();
                if (!matches.containsKey(materialId) || matches.get(materialId) == null && matchedFile != null) {
                    matches.put(materialId, matchedFile);
                }
            }
        }
        return matches.entrySet().stream()
                .map(entry -> result(entry.getKey(), entry.getValue()))
                .filter(result -> accepts(result, filters))
                .sorted(Comparator.comparing(SearchResult::materialCreatedTime).reversed()
                        .thenComparing(SearchResult::id))
                .toList();
    }

    private SearchResult result(UUID materialId, UUID matchedFileId) {
        Material material = materials.findById(materialId).orElseThrow();
        MaterialVersion version = versions.findById(material.getCurrentVersionId()).orElseThrow();
        return new SearchResult(material.getId(), material.getProject().getId(), material.getOwner().getId(), material.getOwner().getUsername(),
                material.getType(), material.getMaterialCreatedTime(), material.getCurrentVersionId(),
                version.getVersionNumber(), version.getTitle(), version.getEventTime(), version.getLocation(),
                version.getManualTags(), matchedFileId);
    }

    private boolean accepts(SearchResult result, SearchFilters filters) {
        return (filters.materialType() == null || result.type() == filters.materialType())
                && (filters.ownerId() == null || result.ownerId().equals(filters.ownerId()))
                && notBefore(result.materialCreatedTime(), filters.createdFrom())
                && notAfter(result.materialCreatedTime(), filters.createdTo())
                && notBefore(result.eventTime(), filters.eventFrom())
                && notAfter(result.eventTime(), filters.eventTo())
                && contains(result.location(), filters.location())
                && hasTag(result.manualTags(), filters.tag());
    }

    private boolean notBefore(Instant value, Instant lower) {
        return lower == null || value != null && !value.isBefore(lower);
    }

    private boolean notAfter(Instant value, Instant upper) {
        return upper == null || value != null && !value.isAfter(upper);
    }

    private boolean contains(String value, String requested) {
        String needle = normalize(requested);
        return needle == null || value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean hasTag(List<String> values, String requested) {
        String needle = normalize(requested);
        return needle == null || values.stream().anyMatch(value -> value.equalsIgnoreCase(needle));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String escapeLike(String value) {
        return value == null ? null : value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public record SearchFilters(String q, MaterialType materialType, UUID ownerId,
                                Instant createdFrom, Instant createdTo,
                                Instant eventFrom, Instant eventTo,
                                String location, String tag) {
    }

    public record SearchResult(UUID id, UUID projectId, UUID ownerId, String ownerUsername, MaterialType type,
                               Instant materialCreatedTime, UUID currentVersionId, int currentVersionNumber,
                               String title, Instant eventTime, String location, List<String> manualTags,
                               UUID matchedFileId) {
    }
}
