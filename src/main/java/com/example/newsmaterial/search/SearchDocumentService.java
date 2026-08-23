package com.example.newsmaterial.search;

import com.example.newsmaterial.analysis.ImageAnalysis;
import com.example.newsmaterial.analysis.ImageAnalysisRepository;
import com.example.newsmaterial.analysis.ImageAnalysisStatus;
import com.example.newsmaterial.file.FileAsset;
import com.example.newsmaterial.file.VersionFile;
import com.example.newsmaterial.file.VersionFileRepository;
import com.example.newsmaterial.material.domain.Material;
import com.example.newsmaterial.material.domain.MaterialVersion;
import com.example.newsmaterial.material.domain.VersionContentKind;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SearchDocumentService {
    private final SearchDocumentRepository documents;
    private final VersionFileRepository versionFiles;
    private final ImageAnalysisRepository analyses;

    public SearchDocumentService(SearchDocumentRepository documents,
                                 VersionFileRepository versionFiles,
                                 ImageAnalysisRepository analyses) {
        this.documents = documents;
        this.versionFiles = versionFiles;
        this.analyses = analyses;
    }

    public void rebuildCurrent(Material material, MaterialVersion version) {
        upsertMaterial(material.getId(), version.getId(), materialContent(version));
        if (version.getContentKind() == VersionContentKind.FILE) {
            for (VersionFile versionFile : versionFiles.findByVersion_IdOrderByDisplayOrder(version.getId())) {
                analyses.findByFile_Id(versionFile.getFile().getId())
                        .filter(analysis -> analysis.getStatus() == ImageAnalysisStatus.COMPLETED)
                        .ifPresent(analysis -> upsertImage(material.getId(), version.getId(), analysis));
            }
        }
    }

    public void indexCompletedAnalysis(ImageAnalysis analysis) {
        FileAsset file = analysis.getFile();
        Material material = file.getMaterial();
        UUID currentVersionId = material.getCurrentVersionId();
        if (versionFiles.existsByVersion_IdAndFile_Id(currentVersionId, file.getId())) {
            upsertImage(material.getId(), currentVersionId, analysis);
        }
    }

    private void upsertMaterial(UUID materialId, UUID versionId, String content) {
        SearchDocument document = documents
                .findByMaterialIdAndCurrentVersionIdAndFileIdIsNullAndSourceType(
                        materialId, versionId, SearchSourceType.MATERIAL)
                .orElseGet(() -> new SearchDocument(
                        UUID.randomUUID(), materialId, versionId, null, SearchSourceType.MATERIAL, content));
        document.replaceContent(content);
        documents.save(document);
    }

    private void upsertImage(UUID materialId, UUID versionId, ImageAnalysis analysis) {
        UUID fileId = analysis.getFile().getId();
        String content = join(analysis.getAiDescription(), analysis.getAiTags());
        SearchDocument document = documents
                .findByMaterialIdAndCurrentVersionIdAndFileIdAndSourceType(
                        materialId, versionId, fileId, SearchSourceType.IMAGE_AI)
                .orElseGet(() -> new SearchDocument(
                        UUID.randomUUID(), materialId, versionId, fileId, SearchSourceType.IMAGE_AI, content));
        document.replaceContent(content);
        documents.save(document);
    }

    private String materialContent(MaterialVersion version) {
        String versionText = version.getContentKind() == VersionContentKind.TEXT
                ? version.getTextBody() : version.getFileTextContent();
        return join(version.getTitle(), version.getLocation(), version.getManualTags(), versionText);
    }

    private String join(Object... values) {
        List<String> parts = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                parts.add(text);
            } else if (value instanceof List<?> list) {
                list.stream().filter(String.class::isInstance).map(String.class::cast)
                        .filter(text -> !text.isBlank()).forEach(parts::add);
            }
        }
        return String.join("\n", parts);
    }
}
