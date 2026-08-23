package com.example.newsmaterial.analysis;

import com.example.newsmaterial.storage.StoragePort;
import com.example.newsmaterial.search.SearchDocumentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class AnalysisWorker {
    private final ImageAnalysisRepository analyses;
    private final StoragePort storage;
    private final ImageAnalysisProvider provider;
    private final TransactionTemplate transactions;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Consumer<ImageAnalysis> completionIndexer;

    @Autowired
    public AnalysisWorker(ImageAnalysisRepository analyses,
                          StoragePort storage,
                          ImageAnalysisProvider provider,
                          PlatformTransactionManager transactionManager,
                          @Value("${app.analysis.max-attempts:3}") int maxAttempts,
                          @Value("${app.analysis.base-backoff:PT1M}") Duration baseBackoff,
                          SearchDocumentService searchDocuments) {
        this(analyses, storage, provider, transactionManager, maxAttempts, baseBackoff,
                searchDocuments::indexCompletedAnalysis);
    }

    public AnalysisWorker(ImageAnalysisRepository analyses,
                          StoragePort storage,
                          ImageAnalysisProvider provider,
                          PlatformTransactionManager transactionManager,
                          int maxAttempts,
                          Duration baseBackoff) {
        this(analyses, storage, provider, transactionManager, maxAttempts, baseBackoff, analysis -> {
        });
    }

    private AnalysisWorker(ImageAnalysisRepository analyses,
                           StoragePort storage,
                           ImageAnalysisProvider provider,
                           PlatformTransactionManager transactionManager,
                           int maxAttempts,
                           Duration baseBackoff,
                           Consumer<ImageAnalysis> completionIndexer) {
        this.analyses = analyses;
        this.storage = storage;
        this.provider = provider;
        this.transactions = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
        this.completionIndexer = completionIndexer;
    }

    @Scheduled(fixedDelayString = "${app.analysis.poll-delay-ms:5000}")
    public void scheduledRun() {
        Instant now = Instant.now();
        for (int processed = 0; processed < 10 && processNext(now); processed++) {
            // Bound each scheduled batch so other application work is never starved.
        }
    }

    public boolean processNext(Instant now) {
        Claim claim = transactions.execute(status -> claim(now));
        if (claim == null) {
            return false;
        }
        try {
            byte[] bytes = storage.readBytes(claim.objectKey());
            ImageAnalysisResult result = provider.analyze(bytes, claim.mimeType());
            if (result == null) {
                throw new IllegalArgumentException("AI provider returned no result");
            }
            transactions.executeWithoutResult(status -> complete(claim.analysisId(), result, now));
        } catch (RuntimeException failure) {
            transactions.executeWithoutResult(status -> fail(claim.analysisId(), now));
        }
        return true;
    }

    private Claim claim(Instant now) {
        List<ImageAnalysis> eligible = analyses.findEligible(
                ImageAnalysisStatus.PENDING, now, PageRequest.of(0, 1));
        if (eligible.isEmpty()) {
            return null;
        }
        ImageAnalysis analysis = eligible.getFirst();
        analysis.claim(now);
        return new Claim(analysis.getId(), analysis.getFile().getObjectKey(), analysis.getFile().getMimeType());
    }

    private void complete(UUID analysisId, ImageAnalysisResult result, Instant now) {
        ImageAnalysis analysis = analyses.findByIdForUpdate(analysisId).orElseThrow();
        analysis.complete(result.description(), result.tags(), now);
        completionIndexer.accept(analysis);
    }

    private void fail(UUID analysisId, Instant now) {
        ImageAnalysis analysis = analyses.findByIdForUpdate(analysisId).orElseThrow();
        analysis.recordFailure(now, maxAttempts, baseBackoff);
    }

    private record Claim(UUID analysisId, String objectKey, String mimeType) {
    }
}
