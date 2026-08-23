package com.example.newsmaterial.search;

import com.example.newsmaterial.material.persistence.MaterialRepository;
import com.example.newsmaterial.material.persistence.MaterialVersionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SearchReconciliationJob {
    private final MaterialRepository materials;
    private final MaterialVersionRepository versions;
    private final SearchDocumentService documents;

    public SearchReconciliationJob(MaterialRepository materials,
                                   MaterialVersionRepository versions,
                                   SearchDocumentService documents) {
        this.materials = materials;
        this.versions = versions;
        this.documents = documents;
    }

    @Scheduled(fixedDelayString = "${app.search.reconcile-delay-ms:86400000}")
    @Transactional
    public void rebuildAllCurrentDocuments() {
        materials.findAll().forEach(material ->
                versions.findById(material.getCurrentVersionId())
                        .ifPresent(version -> documents.rebuildCurrent(material, version)));
    }
}
