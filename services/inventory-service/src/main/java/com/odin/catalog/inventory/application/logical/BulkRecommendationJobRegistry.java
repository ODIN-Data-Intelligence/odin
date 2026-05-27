package com.odin.catalog.inventory.application.logical;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BulkRecommendationJobRegistry {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    public record Job(
        UUID jobId,
        UUID modelId,
        Status status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        String error
    ) {}

    private final ConcurrentHashMap<UUID, Job> jobs = new ConcurrentHashMap<>();

    public UUID register(UUID modelId) {
        UUID jobId = UUID.randomUUID();
        jobs.put(jobId, new Job(jobId, modelId, Status.PENDING, OffsetDateTime.now(), null, null));
        return jobId;
    }

    public void markRunning(UUID jobId) {
        update(jobId, Status.RUNNING, null, null);
    }

    public void markCompleted(UUID jobId) {
        update(jobId, Status.COMPLETED, OffsetDateTime.now(), null);
    }

    public void markFailed(UUID jobId, String error) {
        update(jobId, Status.FAILED, OffsetDateTime.now(), error);
    }

    public Optional<Job> get(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    private void update(UUID jobId, Status status, OffsetDateTime completedAt, String error) {
        jobs.computeIfPresent(jobId, (k, j) ->
            new Job(j.jobId(), j.modelId(), status, j.createdAt(), completedAt, error));
    }
}
