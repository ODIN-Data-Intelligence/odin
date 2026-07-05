package com.odin.catalog.inventory.application.logical;

import com.odin.catalog.inventory.infrastructure.jpa.entity.BulkRecommendationJobEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.BulkRecommendationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks AI bulk-recommendation jobs in Postgres so that any inventory-service replica can serve a
 * poll for a job started on another replica (previously an in-JVM ConcurrentHashMap, which returned
 * 404 when the poll was load-balanced to a different instance).
 */
@Component
public class BulkRecommendationJobRegistry {

    private static final Logger log = LoggerFactory.getLogger(BulkRecommendationJobRegistry.class);

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    public record Job(
        UUID jobId,
        UUID modelId,
        Status status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        String error
    ) {}

    private final BulkRecommendationJobRepository repository;

    public BulkRecommendationJobRegistry(BulkRecommendationJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UUID register(UUID modelId) {
        BulkRecommendationJobEntity entity = new BulkRecommendationJobEntity();
        entity.setModelId(modelId);
        entity.setStatus(Status.PENDING.name());
        entity.setCreatedAt(OffsetDateTime.now());
        UUID jobId = repository.save(entity).getId();
        log.debug("action=JOB_REGISTER modelId={} jobId={}", modelId, jobId);
        return jobId;
    }

    @Transactional
    public void markRunning(UUID jobId) {
        log.debug("action=JOB_RUNNING jobId={}", jobId);
        update(jobId, Status.RUNNING, null, null);
    }

    @Transactional
    public void markCompleted(UUID jobId) {
        log.debug("action=JOB_COMPLETED jobId={}", jobId);
        update(jobId, Status.COMPLETED, OffsetDateTime.now(), null);
    }

    @Transactional
    public void markFailed(UUID jobId, String error) {
        log.debug("action=JOB_FAILED jobId={} error={}", jobId, error);
        update(jobId, Status.FAILED, OffsetDateTime.now(), error);
    }

    @Transactional(readOnly = true)
    public Optional<Job> get(UUID jobId) {
        return repository.findById(jobId).map(this::toJob);
    }

    private void update(UUID jobId, Status status, OffsetDateTime completedAt, String error) {
        repository.findById(jobId).ifPresent(entity -> {
            entity.setStatus(status.name());
            entity.setCompletedAt(completedAt);
            entity.setError(error);
            repository.save(entity);
        });
    }

    private Job toJob(BulkRecommendationJobEntity entity) {
        return new Job(
            entity.getId(),
            entity.getModelId(),
            Status.valueOf(entity.getStatus()),
            entity.getCreatedAt(),
            entity.getCompletedAt(),
            entity.getError()
        );
    }
}
