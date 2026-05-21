package com.odin.catalog.shared.models.openlineage;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * OpenLineage RunEvent — the primary event type emitted by data pipelines.
 * Spec: https://openlineage.io/spec/1-0-5/OpenLineage.json#/$defs/RunEvent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunEvent(
    String eventType,       // START | RUNNING | COMPLETE | FAIL | ABORT | OTHER
    String eventTime,       // ISO-8601 with timezone
    Run run,
    Job job,
    List<InputDataset> inputs,
    List<OutputDataset> outputs,
    String producer,        // URI identifying the producer
    String schemaURL        // URI of the OpenLineage schema version
) {}
