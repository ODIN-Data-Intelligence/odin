package com.odin.catalog.inventory.infrastructure.kafka;

import com.odin.catalog.inventory.application.harvest.HarvestEntityProcessor;
import com.odin.catalog.shared.kafka.consumer.KafkaEventConsumer;
import com.odin.catalog.shared.kafka.topics.CatalogTopics;
import com.odin.catalog.shared.models.events.HarvestEntityDiscoveredPayload;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HarvestEntityConsumer {

    private static final Logger log = LoggerFactory.getLogger(HarvestEntityConsumer.class);

    private final KafkaEventConsumer kafkaEventConsumer;
    private final HarvestEntityProcessor processor;

    @KafkaListener(
        topics = CatalogTopics.HARVEST_ENTITIES,
        groupId = "inventory-consumer-harvest",
        concurrency = "4"
    )
    public void onHarvestEntity(ConsumerRecord<String, Object> record) {
        try {
            var envelope = kafkaEventConsumer.unwrap(record, HarvestEntityDiscoveredPayload.class);
            processor.process(envelope.payload());
        } catch (Exception e) {
            log.error("Failed to process harvest entity from offset {}: {}",
                record.offset(), e.getMessage(), e);
        }
    }
}
