package com.odin.catalog.shared.models.events;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HarvestRunStatusPayload(
    String runId,
    String jobId,
    String sourceId,
    String status,           // pending, running, completed, failed, cancelled
    String triggeredBy,
    String startedAt,        // ISO-8601
    String completedAt,      // ISO-8601
    Integer entitiesDiscovered,
    Integer entitiesCreated,
    Integer entitiesUpdated,
    Integer entitiesFailed,
    String errorMessage
) {}
