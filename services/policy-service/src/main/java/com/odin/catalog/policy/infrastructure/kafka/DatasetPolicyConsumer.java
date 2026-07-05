package com.odin.catalog.policy.infrastructure.kafka;

import com.odin.catalog.policy.application.PolicyRegistryService;
import com.odin.catalog.shared.kafka.consumer.KafkaEventConsumer;
import com.odin.catalog.shared.kafka.topics.CatalogTopics;
import com.odin.catalog.shared.models.events.DatasetChangedPayload;
import com.odin.catalog.shared.models.policy.PolicyComponentPayload;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DatasetPolicyConsumer {

    private static final Logger log = LoggerFactory.getLogger(DatasetPolicyConsumer.class);

    private final KafkaEventConsumer kafkaEventConsumer;
    private final PolicyRegistryService registryService;

    @KafkaListener(
        topics = CatalogTopics.DATASETS_CHANGES,
        groupId = "policy-consumer-datasets",
        concurrency = "2"
    )
    public void onDatasetChanged(ConsumerRecord<String, Object> record) {
        log.debug("action=EVENT_RECEIVED topic={} offset={} key={}", record.topic(), record.offset(), record.key());
        try {
            var envelope = kafkaEventConsumer.unwrap(record, DatasetChangedPayload.class);
            DatasetChangedPayload payload = envelope.payload();

            List<PolicyComponentPayload> components = payload.policyComponents();
            String changeType = payload.changeType();

            if (components != null && !components.isEmpty()) {
                // Derived/accepted terms path — store pieces, link, assemble
                registryService.upsertFromComponents(payload.datasetId(), payload.tenantId(), components);
                log.info("action=COMPONENTS_SYNCED datasetId={} tenantId={} pieces={}",
                    payload.datasetId(), payload.tenantId(), components.size());

            } else if (payload.hasPolicy() != null && !payload.hasPolicy().isBlank()) {
                // Manual PUT or legacy event path — upsert policy directly (no pieces)
                registryService.upsertFromEvent(payload.datasetId(), payload.tenantId(), payload.hasPolicy());
                log.info("action=POLICY_SYNCED datasetId={} tenantId={}", payload.datasetId(), payload.tenantId());

            } else if ("UPDATED".equals(changeType) || "DELETED".equals(changeType)) {
                // Policy reset or dataset deleted — remove links and policy record
                UUID did = UUID.fromString(payload.datasetId());
                UUID tid = UUID.fromString(payload.tenantId());
                registryService.deleteLinks(did, tid);
                registryService.delete(did, tid);
                log.info("action=POLICY_REMOVED datasetId={} tenantId={}", payload.datasetId(), payload.tenantId());
            }

        } catch (Exception e) {
            log.error("action=EVENT_PROCESSING_FAILED topic={} offset={} error={}",
                record.topic(), record.offset(), e.getMessage(), e);
        }
    }
}
