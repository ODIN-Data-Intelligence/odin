package com.odin.catalog.shared.kafka.producer;

import com.odin.catalog.shared.models.events.KafkaEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String producerService;
    private final String schemaVersion;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               String producerService,
                               String schemaVersion) {
        this.kafkaTemplate = kafkaTemplate;
        this.producerService = producerService;
        this.schemaVersion = schemaVersion;
    }

    public <T> CompletableFuture<SendResult<String, Object>> publish(
            String topic, String key, String eventType, String tenantId, T payload) {

        KafkaEnvelope<T> envelope = new KafkaEnvelope<>(
            UUID.randomUUID().toString(),
            eventType,
            schemaVersion,
            producerService,
            tenantId,
            Instant.now().toString(),
            payload
        );

        return kafkaTemplate.send(topic, key, envelope)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event [{}] to topic [{}]: {}", eventType, topic, ex.getMessage());
                } else {
                    log.debug("Published event [{}] to topic [{}] partition [{}] offset [{}]",
                        eventType, topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }

    // Fire-and-forget variant (logs errors, does not propagate them)
    public <T> void publishAsync(String topic, String key, String eventType, String tenantId, T payload) {
        publish(topic, key, eventType, tenantId, payload)
            .exceptionally(ex -> {
                log.error("Unhandled publish failure [{}] to topic [{}]", eventType, topic, ex);
                return null;
            });
    }
}
