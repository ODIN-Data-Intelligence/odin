package com.odin.catalog.shared.models.events;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KafkaEnvelope<T>(
    String eventId,
    String eventType,
    String schemaVersion,
    String producerService,
    String tenantId,
    String timestamp,
    T payload
) {}
