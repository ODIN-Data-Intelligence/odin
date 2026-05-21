package com.odin.catalog.shared.models.openlineage.facets;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenLineage nominal time run facet — the scheduled window for the run.
 * Key in run facets map: "nominalTime".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NominalTimeRunFacet(
    String _producer,
    String _schemaURL,
    String nominalStartTime,    // ISO-8601
    String nominalEndTime       // ISO-8601, optional
) {}
