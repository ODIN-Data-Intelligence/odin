package com.odin.catalog.shared.models.openlineage.facets;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenLineage data source dataset facet — the system the dataset originates from.
 * Key in dataset facets map: "dataSource".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataSourceDatasetFacet(
    String _producer,
    String _schemaURL,
    String name,        // e.g. "snowflake", "glue", "teradata"
    String uri          // connection URI
) {}
