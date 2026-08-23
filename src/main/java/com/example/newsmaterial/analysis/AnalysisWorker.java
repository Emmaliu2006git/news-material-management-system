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
    private final Duration leaseDuration;

    @Autowired
    public AnalysisWorker(ImageAnalysisRepository analyses,
                          StoragePort storage,
                          ImageAnalysisProvider provider,
                          PlatformTransactionManager transactionManager,
                          @Value("${app.analysis.max-attempts:3}") int maxAttempts,
                          @Value("${app.analysis.base-backoff:PT1M}") Duration baseBackoff,
                          @Value("${app.analysis.lease-duration:PT5M}") Duration leaseDuration,
                          SearchDocumentService searchDocuments) {
        this(analyses, storage, provider, transactionManager, maxAttempts, baseBackoff, leaseDuration,
                searchDocuments::indexCompletedAnalysis);
    }

    public AnalysisWorker(ImageAnalysisRepository analyses,
                          StoragePort storage,
                          ImageAnalysisProvider provider,
                          PlatformTransactionManager transactionManager,
                          int maxAttempts,
                          Duration baseBackoff) {
        this(analyses, storage, provider, transactionManager, maxAttempts, baseBackoff,
                Duration.ofMinutes(5));
    }

    public AnalysisWorker(ImageAnalysisRepository analyses,
                          StoragePort storage,
                          ImageAnalysisProvider provider,
                          PlatformTransactionManager transactionManager,
                          int maxAttempts,
                          Duration baseBackoff,
                          Duration leaseDuration) {
        this(analyses, storage, provider, transactionManager, maxAttempts, baseBackoff, leaseDuration,
                analysis -> { });
    }

    private AnalysisWorker(ImageAnalysisRepository analyses,
                           StoragePort storage,
                           ImageAnalysisProvider provider,
                           PlatformTransactionManager transactionManager,
                           int maxAttempts,
                           Duration baseBackoff,
                           Duration leaseDuration,
                           Consumer<ImageAnalysis> completionIndexer) {
        this.analyses = analyses;
        this.storage = storage;
        this.provider = provider;
        this.transactions = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
        this.leaseDuration = leaseDuration;
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
        if (!claim.shouldProcess()) {
            return true;
        }
        try {
            byte[] bytes = storage.readBytes(claim.objectKey());
            ImageAnalysisResult result = provider.analyze(bytes, claim.mimeType());
            if (result == null) {
                throw new IllegalArgumentException("AI provider returned no result");
            }
            transactions.executeWithoutResult(status -> complete(claim.analysisId(), claim.attempt(), result, now));
        } catch (RuntimeException failure) {
            transactions.executeWithoutResult(status -> fail(claim.analysisId(), claim.attempt(), now));
        }
        return true;
    }

    private Claim claim(Instant now) {
        List<ImageAnalysis> eligible = analyses.findEligible(
                ImageAnalysisStatus.PENDING, ImageAnalysisStatus.PROCESSING,
                now, PageRequest.of(0, 1));
        if (eligible.isEmpty()) {
            return null;
        }
        ImageAnalysis analysis = eligible.getFirst();
        boolean shouldProcess = analysis.claim(now, leaseDuration, maxAttempts);
        return new Claim(analysis.getId(),
                shouldProcess ? analysis.getFile().getObjectKey() : null,
                shouldProcess ? analysis.getFile().getMimeType() : null,
                analysis.getAttemptCount(), shouldProcess);
    }

    private void complete(UUID analysisId, int attempt, ImageAnalysisResult result, Instant now) {
        ImageAnalysis analysis = analyses.findByIdForUpdate(analysisId).orElseThrow();
        if (analysis.getStatus() != ImageAnalysisStatus.PROCESSING || analysis.getAttemptCount() != attempt) {
            return;
        }
        analysis.complete(result.description(), result.tags(), now);
        completionIndexer.accept(analysis);
    }

    private void fail(UUID analysisId, int attempt, Instant now) {
        ImageAnalysis analysis = analyses.findByIdForUpdate(analysisId).orElseThrow();
        if (analysis.getStatus() != ImageAnalysisStatus.PROCESSING || analysis.getAttemptCount() != attempt) {
            return;
        }
        analysis.recordFailure(now, maxAttempts, baseBackoff);
    }

    private record Claim(UUID analysisId, String objectKey, String mimeType,
                         int attempt, boolean shouldProcess) {
    }
}
