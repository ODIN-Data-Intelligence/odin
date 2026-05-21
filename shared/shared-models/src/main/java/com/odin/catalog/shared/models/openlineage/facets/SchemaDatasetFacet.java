package com.odin.catalog.shared.models.openlineage.facets;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * OpenLineage schema dataset facet — describes column names and types.
 * Key in dataset facets map: "schema".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchemaDatasetFacet(
    String _producer,
    String _schemaURL,
    List<SchemaField> fields
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SchemaField(
        String name,
        String type,
        String description,
        List<SchemaField> fields   // nested fields for structs
    ) {}
}
