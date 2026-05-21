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
        try {
            var envelope = kafkaEventConsumer.unwrap(record, HarvestDdlDiscoveredPayload.class);
            ddlParser.process(envelope.payload());
        } catch (Exception e) {
            log.error("Failed to process DDL from offset {}: {}", record.offset(), e.getMessage(), e);
        }
    }
}
