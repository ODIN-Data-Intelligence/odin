package com.odin.catalog.lineage.infrastructure.kafka;

import com.odin.catalog.lineage.ingestion.DdlLineageParser;
import com.odin.catalog.shared.kafka.consumer.KafkaEventConsumer;
import com.odin.catalog.shared.kafka.topics.CatalogTopics;
import com.odin.catalog.shared.models.events.HarvestDdlDiscoveredPayload;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HarvestDdlConsumer {

    private static final Logger log = LoggerFactory.getLogger(HarvestDdlConsumer.class);

    private final KafkaEventConsumer kafkaEventConsumer;
    private final DdlLineageParser ddlParser;

    @KafkaListener(
        topics = CatalogTopics.HARVEST_DDL,
        groupId = "lineage-consumer-harvest",
        concurrency = "2"
    )
    public void onDdlDiscovered(ConsumerRecord<String, Object> record) {
        log.debug("action=EVENT_RECEIVED topic={} offset={} key={}", record.topic(), record.offset(), record.key());
        long t = System.currentTimeMillis();
        try {
            var envelope = kafkaEventConsumer.unwrap(record, HarvestDdlDiscoveredPayload.class);
            HarvestDdlDiscoveredPayload payload = envelope.payload();
            ddlParser.process(payload);
            log.info("action=EVENT_PROCESSED topic={} offset={} object={}.{} elapsed={}ms",
                record.topic(), record.offset(), payload.objectNamespace(), payload.objectName(), System.currentTimeMillis() - t);
        } catch (Exception e) {
            log.error("action=EVENT_PROCESSING_FAILED topic={} offset={} error={}",
                record.topic(), record.offset(), e.getMessage(), e);
        }
    }
}
