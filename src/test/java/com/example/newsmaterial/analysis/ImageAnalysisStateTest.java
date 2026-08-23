package com.example.newsmaterial.analysis;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ImageAnalysisStateTest {
    @Test
    void thirdAutomaticFailureExhaustsTheAnalysisUntilAnExplicitRetry() {
        Instant started = Instant.parse("2026-08-22T12:00:00Z");
        ImageAnalysis analysis = ImageAnalysis.pending(UUID.randomUUID(), null, started);

        analysis.claim(started);
        analysis.recordFailure(started, 3, Duration.ofMinutes(1));
        assertThat(analysis.getStatus()).isEqualTo(ImageAnalysisStatus.PENDING);
        assertThat(analysis.getAttemptCount()).isEqualTo(1);
        assertThat(analysis.getNextAttemptTime()).isEqualTo(started.plusSeconds(60));

        Instant second = started.plusSeconds(60);
        analysis.claim(second);
        analysis.recordFailure(second, 3, Duration.ofMinutes(1));
        assertThat(analysis.getStatus()).isEqualTo(ImageAnalysisStatus.PENDING);
        assertThat(analysis.getAttemptCount()).isEqualTo(2);
        assertThat(analysis.getNextAttemptTime()).isEqualTo(second.plusSeconds(120));

        Instant third = second.plusSeconds(120);
        analysis.claim(third);
        analysis.recordFailure(third, 3, Duration.ofMinutes(1));
        assertThat(analysis.getStatus()).isEqualTo(ImageAnalysisStatus.FAILED);
        assertThat(analysis.getAttemptCount()).isEqualTo(3);
        assertThat(analysis.getNextAttemptTime()).isNull();

        Instant retryAt = third.plusSeconds(10);
        analysis.retry(retryAt);
        assertThat(analysis.getStatus()).isEqualTo(ImageAnalysisStatus.PENDING);
        assertThat(analysis.getAttemptCount()).isZero();
        assertThat(analysis.getNextAttemptTime()).isEqualTo(retryAt);
    }

    @Test
    void completionStoresValidatedPerImageResult() {
        Instant started = Instant.parse("2026-08-22T12:00:00Z");
        Instant completed = started.plusSeconds(5);
        ImageAnalysis analysis = ImageAnalysis.pending(UUID.randomUUID(), null, started);

        analysis.claim(started);
        analysis.complete("Flood water surrounding cars", List.of("flood", "vehicles"), completed);

        assertThat(analysis.getStatus()).isEqualTo(ImageAnalysisStatus.COMPLETED);
        assertThat(analysis.getAiDescription()).isEqualTo("Flood water surrounding cars");
        assertThat(analysis.getAiTags()).containsExactly("flood", "vehicles");
        assertThat(analysis.getGeneratedTime()).isEqualTo(completed);
        assertThat(analysis.getNextAttemptTime()).isNull();
    }

    @Test
    void anExpiredClaimStillCountsTowardTheMaximumAttempts() {
        Instant started = Instant.parse("2026-08-22T12:00:00Z");
        Duration lease = Duration.ofSeconds(30);
        Duration backoff = Duration.ofSeconds(10);
        ImageAnalysis analysis = ImageAnalysis.pending(UUID.randomUUID(), null, started);

        analysis.claim(started, lease);
        analysis.claim(started.plus(lease), lease);
        analysis.recordFailure(started.plus(lease), 3, backoff);
        Instant thirdAttempt = analysis.getNextAttemptTime();
        analysis.claim(thirdAttempt, lease);
        analysis.recordFailure(thirdAttempt, 3, backoff);

        assertThat(analysis.getStatus()).isEqualTo(ImageAnalysisStatus.FAILED);
        assertThat(analysis.getAttemptCount()).isEqualTo(3);
        assertThat(analysis.getLeaseExpiresAt()).isNull();
    }
}
