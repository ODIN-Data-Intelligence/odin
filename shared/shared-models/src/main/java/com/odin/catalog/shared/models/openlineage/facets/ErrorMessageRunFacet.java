package com.odin.catalog.shared.models.openlineage.facets;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenLineage error message run facet — failure details.
 * Key in run facets map: "errorMessage".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorMessageRunFacet(
    String _producer,
    String _schemaURL,
    String message,
    String programmingLanguage,
    String stackTrace
) {}
