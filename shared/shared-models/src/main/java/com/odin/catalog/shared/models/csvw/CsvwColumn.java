package com.odin.catalog.shared.models.csvw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * W3C CSV-W Column descriptor.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsvwColumn(
    String name,
    List<String> titles,
    String datatype,                // XSD type IRI or simple name (string, integer, date…)
    @JsonProperty("required") Boolean required,
    @JsonProperty("virtual") Boolean virtual,
    @JsonProperty("suppressOutput") Boolean suppressOutput,
    String lang,                    // BCP-47 language tag
    String defaultValue,
    String propertyUrl,             // semantic IRI for the property
    String valueUrl,                // IRI template for values
    String aboutUrl,                // IRI template for the entity
    String description,
    int ordinal
) {}
