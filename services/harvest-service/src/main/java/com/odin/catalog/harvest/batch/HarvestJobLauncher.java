package com.odin.catalog.harvest.batch;

import com.odin.catalog.harvest.connector.HarvestConnector;
import com.odin.catalog.harvest.connector.HarvestEntity;
import com.odin.catalog.harvest.domain.run.HarvestRun;
import com.odin.catalog.harvest.domain.source.HarvestSource;
import com.odin.catalog.harvest.infrastructure.jpa.repository.HarvestRunRepository;
import com.odin.catalog.harvest.infrastructure.kafka.HarvestEventProducer;
import com.odin.catalog.shared.models.events.HarvestEntityDiscoveredPayload;
import com.odin.catalog.shared.models.events.HarvestRunStatusPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates a harvest run:
 *   1. Look up the connector for the source type
 *   2. Stream entities from the connector
 *   3. Publish each entity to Kafka (harvest.entities.discovered)
 *   4. Publish DDL entities to harvest.ddl.discovered for lineage
 *   5. Update run status in DB
 */
@Service
@RequiredArgsConstructor
public class HarvestJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(HarvestJobLauncher.class);

    private final List<HarvestConnector> connectors;
    private final HarvestRunRepository runRepository;
    private final HarvestEventProducer eventProducer;

    private Map<String, HarvestConnector> connectorMap() {
        return connectors.stream().collect(
            Collectors.toMap(HarvestConnector::sourceType, Function.identity())
        );
    }

    @Async
    public void launch(HarvestRun run, HarvestSource source) {
        HarvestConnector connector = connectorMap().get(source.sourceType());
        if (connector == null) {
            log.error("No connector registered for source type: {}", source.sourceType());
            markFailed(run, "No connector for type: " + source.sourceType());
            return;
        }

        markRunning(run);

        AtomicInteger discovered = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        try {
            connector.harvest(run, source).forEach(entity -> {
                try {
                    publishEntity(entity, source, run);
                    discovered.incrementAndGet();
                } catch (Exception e) {
                    log.error("Failed to publish entity {}: {}", entity.sourceKey(), e.getMessage());
                    failed.incrementAndGet();
                }
            });
            markCompleted(run, discovered.get(), failed.get());
        } catch (Exception e) {
            log.error("Harvest run {} failed: {}", run.id(), e.getMessage(), e);
            markFailed(run, e.getMessage());
        }
    }

    private void publishEntity(HarvestEntity entity, HarvestSource source, HarvestRun run) {
        // Publish to harvest.entities.discovered
        var payload = new HarvestEntityDiscoveredPayload(
            run.id().toString(),
            source.id().toString(),
            source.sourceType(),
            entity.entityType().name(),
            entity.sourceKey(),
            entity.sourceUri(),
            entity.title(),
            entity.description(),
            entity.format(),
            entity.mediaType(),
            entity.keywords(),
            entity.themes(),
            entity.columns(),
            entity.rawPayload()
        );
        eventProducer.publishEntityDiscovered(entity.sourceKey(), source.tenantId().toString(), payload);

        // Publish DDL to harvest.ddl.discovered for lineage extraction
        if (entity.ddl() != null) {
            eventProducer.publishDdlDiscovered(entity, source, run);
        }
    }

    private void markRunning(HarvestRun run) {
        updateRunStatus(run.id(), "running", null, null);
        eventProducer.publishRunStatus(run, "running", null);
    }

    private void markCompleted(HarvestRun run, int discovered, int failed) {
        updateRunStatus(run.id(), "completed", discovered, failed);
        eventProducer.publishRunStatus(run, "completed", null);
    }

    private void markFailed(HarvestRun run, String error) {
        updateRunStatus(run.id(), "failed", null, null);
        eventProducer.publishRunStatus(run, "failed", error);
    }

    private void updateRunStatus(UUID runId, String status, Integer discovered, Integer failed) {
        runRepository.findById(runId).ifPresent(entity -> {
            entity.setStatus(status);
            if ("running".equals(status)) entity.setStartedAt(OffsetDateTime.now());
            if ("completed".equals(status) || "failed".equals(status)) entity.setCompletedAt(OffsetDateTime.now());
            if (discovered != null) entity.setEntitiesDiscovered(discovered);
            if (failed != null) entity.setEntitiesFailed(failed);
            runRepository.save(entity);
        });
    }
}
