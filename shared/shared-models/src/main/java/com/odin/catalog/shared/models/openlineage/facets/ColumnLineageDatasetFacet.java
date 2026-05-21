package com.odin.catalog.shared.models.openlineage.facets;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * OpenLineage column-level lineage facet.
 * Key in output dataset facets map: "columnLineage".
 *
 * <p>Structure: {@code fields} maps each output column name to its input derivation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ColumnLineageDatasetFacet(
    String _producer,
    String _schemaURL,
    Map<String, ColumnLineageField> fields  // outputColumnName → field derivation
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ColumnLineageField(
        List<InputField> inputFields
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InputField(
        String namespace,
        String name,
        String field,               // source column name
        List<Transformation> transformations
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Transformation(
        String type,                // IDENTITY | AGGREGATION | TRANSFORMATION | INDIRECT | MASKED
        String subtype,
        String description,
        Boolean masking
    ) {}
}
