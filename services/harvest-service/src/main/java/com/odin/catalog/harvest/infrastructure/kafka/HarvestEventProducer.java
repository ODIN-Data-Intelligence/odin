package com.odin.catalog.harvest.infrastructure.kafka;

import com.odin.catalog.harvest.connector.HarvestEntity;
import com.odin.catalog.harvest.domain.run.HarvestRun;
import com.odin.catalog.harvest.domain.source.HarvestSource;
import com.odin.catalog.shared.kafka.producer.KafkaEventPublisher;
import com.odin.catalog.shared.kafka.topics.CatalogTopics;
import com.odin.catalog.shared.models.events.HarvestDdlDiscoveredPayload;
import com.odin.catalog.shared.models.events.HarvestEntityDiscoveredPayload;
import com.odin.catalog.shared.models.events.HarvestRunStatusPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HarvestEventProducer {

    private static final Logger log = LoggerFactory.getLogger(HarvestEventProducer.class);

    private final KafkaEventPublisher publisher;

    public void publishEntityDiscovered(String key, String tenantId, HarvestEntityDiscoveredPayload payload) {
        publisher.publishAsync(
            CatalogTopics.HARVEST_ENTITIES,
            key,
            "HarvestEntityDiscovered",
            tenantId,
            payload
        );
        log.info("action=EVENT_PUBLISHED topic={} eventType=HarvestEntityDiscovered entityKey={}", CatalogTopics.HARVEST_ENTITIES, key);
    }

    public void publishDdlDiscovered(HarvestEntity entity, HarvestSource source, HarvestRun run) {
        var payload = new HarvestDdlDiscoveredPayload(
            run.id().toString(),
            source.id().toString(),
            source.sourceType(),
            entity.entityType().name(),
            entity.sourceKey().contains(".") ? entity.sourceKey().substring(0, entity.sourceKey().lastIndexOf('.')) : "",
            entity.sourceKey().contains(".") ? entity.sourceKey().substring(entity.sourceKey().lastIndexOf('.') + 1) : entity.sourceKey(),
            source.sourceType(),  // dialect maps to source type
            entity.ddl()
        );
        publisher.publishAsync(
            CatalogTopics.HARVEST_DDL,
            run.id().toString(),
            "HarvestDdlDiscovered",
            source.tenantId().toString(),
            payload
        );
        log.info("action=EVENT_PUBLISHED topic={} eventType=HarvestDdlDiscovered runId={} object={}.{}",
            CatalogTopics.HARVEST_DDL, run.id(), payload.objectNamespace(), payload.objectName());
    }

    public void publishRunStatus(HarvestRun run, String status, String errorMessage) {
        var payload = new HarvestRunStatusPayload(
            run.id().toString(),
            run.jobId().toString(),
            run.sourceId().toString(),
            status,
            run.triggeredBy(),
            run.startedAt() != null ? run.startedAt().toString() : null,
            null, null, null, null, null,
            errorMessage
        );
        publisher.publishAsync(
            CatalogTopics.HARVEST_RUNS_EVENTS,
            run.id().toString(),
            "HarvestRunStatus",
            null,
            payload
        );
        log.info("action=EVENT_PUBLISHED topic={} eventType=HarvestRunStatus runId={} status={}",
            CatalogTopics.HARVEST_RUNS_EVENTS, run.id(), status);
    }
}
