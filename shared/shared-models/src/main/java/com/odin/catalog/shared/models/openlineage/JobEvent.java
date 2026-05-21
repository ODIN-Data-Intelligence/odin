package com.odin.catalog.shared.models.openlineage;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * OpenLineage JobEvent — emitted when job metadata changes outside of a run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobEvent(
    String eventTime,
    String producer,
    String schemaURL,
    Job job,
    List<InputDataset> inputs,
    List<OutputDataset> outputs
) {}
