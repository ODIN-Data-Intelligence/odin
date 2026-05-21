package com.odin.catalog.shared.models.openlineage;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * OpenLineage DatasetEvent — emitted when a dataset changes outside of a job run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetEvent(
    String eventTime,
    String producer,
    String schemaURL,
    DatasetRef dataset
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DatasetRef(
        String namespace,
        String name,
        Map<String, Object> facets
    ) {}
}
