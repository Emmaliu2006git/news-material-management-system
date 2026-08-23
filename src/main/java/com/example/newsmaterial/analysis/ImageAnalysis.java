package com.example.newsmaterial.analysis;

import com.example.newsmaterial.file.FileAsset;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "image_analyses")
public class ImageAnalysis {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false, unique = true, updatable = false)
    private FileAsset file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImageAnalysisStatus status;

    @Column(name = "ai_description", columnDefinition = "text")
    private String aiDescription;

    @ElementCollection
    @CollectionTable(name = "image_analysis_tags", joinColumns = @JoinColumn(name = "analysis_id"))
    @OrderColumn(name = "position")
    @Column(name = "tag_value", nullable = false, length = 80)
    private List<String> aiTags = new ArrayList<>();

    @Column(name = "generated_time")
    private Instant generatedTime;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_time")
    private Instant nextAttemptTime;

    protected ImageAnalysis() {
    }

    private ImageAnalysis(UUID id, FileAsset file, Instant now) {
        this.id = id;
        this.file = file;
        this.status = ImageAnalysisStatus.PENDING;
        this.nextAttemptTime = now;
    }

    public static ImageAnalysis pending(UUID id, FileAsset file, Instant now) {
        return new ImageAnalysis(id, file, now);
    }

    public void claim(Instant now) {
        if (status != ImageAnalysisStatus.PENDING || nextAttemptTime != null && nextAttemptTime.isAfter(now)) {
            throw new IllegalStateException("Analysis is not eligible");
        }
        status = ImageAnalysisStatus.PROCESSING;
        attemptCount++;
        nextAttemptTime = null;
    }

    public void complete(String description, List<String> tags, Instant generatedAt) {
        if (status != ImageAnalysisStatus.PROCESSING) {
            throw new IllegalStateException("Analysis is not processing");
        }
        status = ImageAnalysisStatus.COMPLETED;
        aiDescription = description;
        aiTags = new ArrayList<>(tags);
        generatedTime = generatedAt;
        nextAttemptTime = null;
    }

    public void recordFailure(Instant now, int maxAttempts, Duration baseBackoff) {
        if (status != ImageAnalysisStatus.PROCESSING) {
            throw new IllegalStateException("Analysis is not processing");
        }
        if (attemptCount >= maxAttempts) {
            status = ImageAnalysisStatus.FAILED;
            nextAttemptTime = null;
            return;
        }
        status = ImageAnalysisStatus.PENDING;
        nextAttemptTime = now.plus(baseBackoff.multipliedBy(attemptCount));
    }

    public void retry(Instant now) {
        if (status != ImageAnalysisStatus.FAILED) {
            throw new IllegalStateException("Only failed analysis can be retried");
        }
        status = ImageAnalysisStatus.PENDING;
        attemptCount = 0;
        nextAttemptTime = now;
    }

    public UUID getId() { return id; }
    public FileAsset getFile() { return file; }
    public ImageAnalysisStatus getStatus() { return status; }
    public String getAiDescription() { return aiDescription; }
    public List<String> getAiTags() { return List.copyOf(aiTags); }
    public Instant getGeneratedTime() { return generatedTime; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptTime() { return nextAttemptTime; }
}
