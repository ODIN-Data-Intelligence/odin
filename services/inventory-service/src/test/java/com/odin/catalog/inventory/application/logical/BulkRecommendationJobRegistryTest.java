package com.odin.catalog.inventory.application.logical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BulkRecommendationJobRegistryTest {

    BulkRecommendationJobRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BulkRecommendationJobRegistry();
    }

    @Test
    void register_createsJobWithPendingStatus() {
        UUID modelId = UUID.randomUUID();

        UUID jobId = registry.register(modelId);

        assertThat(jobId).isNotNull();
        BulkRecommendationJobRegistry.Job job = registry.get(jobId).orElseThrow();
        assertThat(job.jobId()).isEqualTo(jobId);
        assertThat(job.modelId()).isEqualTo(modelId);
        assertThat(job.status()).isEqualTo(BulkRecommendationJobRegistry.Status.PENDING);
        assertThat(job.createdAt()).isNotNull();
        assertThat(job.completedAt()).isNull();
        assertThat(job.error()).isNull();
    }

    @Test
    void markRunning_updatesStatusToRunning() {
        UUID jobId = registry.register(UUID.randomUUID());

        registry.markRunning(jobId);

        assertThat(registry.get(jobId).orElseThrow().status())
            .isEqualTo(BulkRecommendationJobRegistry.Status.RUNNING);
    }

    @Test
    void markCompleted_setsCompletedStatusAndTimestamp() {
        UUID jobId = registry.register(UUID.randomUUID());

        registry.markCompleted(jobId);

        BulkRecommendationJobRegistry.Job job = registry.get(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(BulkRecommendationJobRegistry.Status.COMPLETED);
        assertThat(job.completedAt()).isNotNull();
        assertThat(job.error()).isNull();
    }

    @Test
    void markFailed_setsFailedStatusErrorAndTimestamp() {
        UUID jobId = registry.register(UUID.randomUUID());

        registry.markFailed(jobId, "AI service timeout");

        BulkRecommendationJobRegistry.Job job = registry.get(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(BulkRecommendationJobRegistry.Status.FAILED);
        assertThat(job.error()).isEqualTo("AI service timeout");
        assertThat(job.completedAt()).isNotNull();
    }

    @Test
    void get_unknownJobId_returnsEmpty() {
        assertThat(registry.get(UUID.randomUUID())).isEmpty();
    }

    @Test
    void markRunning_nonExistentJob_isNoOp() {
        assertNoException(() -> registry.markRunning(UUID.randomUUID()));
    }

    @Test
    void markCompleted_nonExistentJob_isNoOp() {
        assertNoException(() -> registry.markCompleted(UUID.randomUUID()));
    }

    @Test
    void markFailed_nonExistentJob_isNoOp() {
        assertNoException(() -> registry.markFailed(UUID.randomUUID(), "err"));
    }

    @Test
    void register_multipleJobs_eachHasUniqueId() {
        UUID modelId = UUID.randomUUID();
        UUID job1 = registry.register(modelId);
        UUID job2 = registry.register(modelId);

        assertThat(job1).isNotEqualTo(job2);
        assertThat(registry.get(job1)).isPresent();
        assertThat(registry.get(job2)).isPresent();
    }

    private static void assertNoException(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new AssertionError("Expected no exception but got: " + e);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
