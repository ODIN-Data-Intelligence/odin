package com.odin.catalog.shared.models.openlineage.facets;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenLineage output statistics facet — row and byte counts for output datasets.
 * Key in output dataset facets map: "outputStatistics".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutputStatisticsOutputDatasetFacet(
    String _producer,
    String _schemaURL,
    Long rowCount,
    Long size,          // bytes
    Long fileCount
) {}
