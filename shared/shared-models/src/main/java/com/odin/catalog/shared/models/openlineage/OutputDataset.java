package com.odin.catalog.shared.models.openlineage;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutputDataset(
    String namespace,
    String name,
    Map<String, Object> facets,
    Map<String, Object> outputFacets
) {}
