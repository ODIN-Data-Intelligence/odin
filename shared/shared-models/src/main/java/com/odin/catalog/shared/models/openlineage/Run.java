package com.odin.catalog.shared.models.openlineage;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Run(
    String runId,               // UUID
    Map<String, Object> facets
) {}
