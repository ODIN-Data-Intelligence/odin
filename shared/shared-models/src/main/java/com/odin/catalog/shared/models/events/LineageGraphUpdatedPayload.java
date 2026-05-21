package com.odin.catalog.shared.models.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LineageGraphUpdatedPayload(
    String runId,
    String jobNamespace,
    String jobName,
    String eventType,        // START, RUNNING, COMPLETE, FAIL, ABORT, OTHER
    List<DatasetRef> inputs,
    List<DatasetRef> outputs,
    String updatedAt         // ISO-8601
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DatasetRef(
        String namespace,
        String name,
        String catalogResourceId  // soft FK to inventory-service resource, may be null
    ) {}
}
