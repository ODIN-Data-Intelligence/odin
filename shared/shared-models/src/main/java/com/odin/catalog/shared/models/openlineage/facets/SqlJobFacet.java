package com.odin.catalog.shared.models.openlineage.facets;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenLineage SQL job facet — the query executed by this job.
 * Key in job facets map: "sql".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SqlJobFacet(
    String _producer,
    String _schemaURL,
    String query
) {}
