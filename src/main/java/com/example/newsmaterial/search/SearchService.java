package com.example.newsmaterial.search;

import com.example.newsmaterial.common.api.ApiException;
import com.example.newsmaterial.material.domain.Material;
import com.example.newsmaterial.material.domain.MaterialType;
import com.example.newsmaterial.material.domain.MaterialVersion;
import com.example.newsmaterial.material.persistence.MaterialRepository;
import com.example.newsmaterial.material.persistence.MaterialVersionRepository;
import com.example.newsmaterial.file.VersionFileRepository;
import com.example.newsmaterial.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SearchService {
    private final ProjectRepository projects;
    private final MaterialRepository materials;
    private final MaterialVersionRepository versions;
    private final SearchDocumentRepository documents;
    private final VersionFileRepository versionFiles;

    public SearchService(ProjectRepository projects, MaterialRepository materials,
                         MaterialVersionRepository versions, SearchDocumentRepository documents,
                         VersionFileRepository versionFiles) {
        this.projects = projects;
        this.materials = materials;
        this.versions = versions;
        this.documents = documents;
        this.versionFiles = versionFiles;
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
                .map(entry -> result(entry.getKey(), entry.getValue(), query == null ? 0 : 1))
                .filter(result -> accepts(result, filters))
                .sorted(Comparator.comparing(SearchResult::materialCreatedTime).reversed()
                        .thenComparing(SearchResult::id))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SearchResult> globalSearch(GlobalSearchFilters filters) {
        Set<String> terms = queryTerms(filters.q());
        Map<UUID, Set<String>> matches = new HashMap<>();
        Map<UUID, UUID> matchedFiles = new HashMap<>();

        if (terms.isEmpty()) {
            materials.findAll().forEach(material -> matches.put(material.getId(), Set.of()));
        } else {
            for (SearchDocument document : documents.findAllCurrent()) {
                String content = globalKeywordContent(document).toLowerCase(Locale.ROOT);
                Set<String> documentMatches = new HashSet<>();
                for (String term : terms) {
                    if (content.contains(term)) {
                        documentMatches.add(term);
                    }
                }
                if (!documentMatches.isEmpty()) {
                    matches.computeIfAbsent(document.getMaterialId(), ignored -> new HashSet<>())
                            .addAll(documentMatches);
                    if (document.getSourceType() == SearchSourceType.IMAGE_AI) {
                        matchedFiles.putIfAbsent(document.getMaterialId(), document.getFileId());
                    }
                }
            }
        }

        Comparator<SearchResult> ranking = Comparator
                .comparingInt(SearchResult::matchedTermCount).reversed()
                .thenComparing(SearchResult::eventTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SearchResult::materialCreatedTime, Comparator.reverseOrder())
                .thenComparing(SearchResult::id);

        return matches.entrySet().stream()
                .map(entry -> result(entry.getKey(), matchedFiles.get(entry.getKey()), entry.getValue().size()))
                .filter(result -> accepts(result, filters))
                .sorted(ranking)
                .toList();
    }

    private String globalKeywordContent(SearchDocument document) {
        if (document.getSourceType() == SearchSourceType.IMAGE_AI) {
            return document.getContent();
        }
        Material material = materials.findById(document.getMaterialId()).orElseThrow();
        MaterialVersion version = versions.findById(material.getCurrentVersionId()).orElseThrow();
        StringBuilder content = new StringBuilder(version.getTitle());
        version.getManualTags().forEach(tag -> content.append('\n').append(tag));
        String versionText = material.getType() == MaterialType.TEXT
                ? version.getTextBody() : version.getFileTextContent();
        if (versionText != null && !versionText.isBlank()) {
            content.append('\n').append(versionText);
        }
        return content.toString();
    }

    private SearchResult result(UUID materialId, UUID matchedFileId, int matchedTermCount) {
        Material material = materials.findById(materialId).orElseThrow();
        MaterialVersion version = versions.findById(material.getCurrentVersionId()).orElseThrow();
        return new SearchResult(material.getId(), material.getProject().getId(), material.getProject().getTitle(),
                material.getOwner().getId(), material.getOwner().getUsername(),
                material.getType(), material.getMaterialCreatedTime(), material.getCurrentVersionId(),
                version.getVersionNumber(), version.getTitle(), version.getEventTime(), version.getLocation(),
                version.getManualTags(), matchedFileId, matchedTermCount);
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

    private boolean accepts(SearchResult result, GlobalSearchFilters filters) {
        return matchesFileType(result, filters.fileType())
                && notBefore(result.eventTime(), filters.eventFrom())
                && notAfter(result.eventTime(), filters.eventTo())
                && contains(result.location(), filters.location());
    }

    private boolean matchesFileType(SearchResult result, SearchFileType requested) {
        if (requested == null || requested == SearchFileType.ALL) {
            return true;
        }
        if (requested == SearchFileType.TEXT) {
            return result.type() == MaterialType.TEXT;
        }
        if (result.type() != MaterialType.FILE) {
            return false;
        }
        return versionFiles.findByVersion_IdOrderByDisplayOrder(result.currentVersionId()).stream()
                .map(versionFile -> versionFile.getFile().getMimeType().toLowerCase(Locale.ROOT))
                .anyMatch(mimeType -> switch (requested) {
                    case IMAGE -> mimeType.startsWith("image/");
                    case VIDEO -> mimeType.startsWith("video/");
                    case AUDIO -> mimeType.startsWith("audio/");
                    case OTHER -> !mimeType.startsWith("image/")
                            && !mimeType.startsWith("video/")
                            && !mimeType.startsWith("audio/");
                    case ALL, TEXT -> false;
                });
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

    private Set<String> queryTerms(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String part : query.split(",")) {
            String term = normalize(part);
            if (term != null) {
                terms.add(term);
            }
        }
        return terms;
    }

    public record SearchFilters(String q, MaterialType materialType, UUID ownerId,
                                Instant createdFrom, Instant createdTo,
                                Instant eventFrom, Instant eventTo,
                                String location, String tag) {
    }

    public record GlobalSearchFilters(String q, SearchFileType fileType,
                                      Instant eventFrom, Instant eventTo, String location) {
    }

    public record SearchResult(UUID id, UUID projectId, String projectTitle,
                               UUID ownerId, String ownerUsername, MaterialType type,
                               Instant materialCreatedTime, UUID currentVersionId, int currentVersionNumber,
                               String title, Instant eventTime, String location, List<String> manualTags,
                               UUID matchedFileId, int matchedTermCount) {
    }
}
