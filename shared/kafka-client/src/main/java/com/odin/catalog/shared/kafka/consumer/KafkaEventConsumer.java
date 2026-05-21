package com.odin.catalog.shared.kafka.consumer;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.shared.models.events.KafkaEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for deserializing typed KafkaEnvelope payloads in @KafkaListener methods.
 *
 * Usage in a consumer bean:
 *   KafkaEnvelope<DatasetChangedPayload> envelope =
 *       kafkaEventConsumer.unwrap(record, DatasetChangedPayload.class);
 */
public class KafkaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private final ObjectMapper objectMapper;

    public KafkaEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> KafkaEnvelope<T> unwrap(ConsumerRecord<String, Object> record, Class<T> payloadType) {
        try {
            JavaType type = objectMapper.getTypeFactory()
                .constructParametricType(KafkaEnvelope.class, payloadType);
            String json = objectMapper.writeValueAsString(record.value());
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to deserialize envelope from topic [{}] partition [{}] offset [{}]",
                record.topic(), record.partition(), record.offset(), e);
            throw new KafkaDeserializationException("Failed to unwrap KafkaEnvelope<" + payloadType.getSimpleName() + ">", e);
        }
    }
}
