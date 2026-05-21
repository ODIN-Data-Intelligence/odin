package com.odin.catalog.shared.models.csvw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsvwTable(
    String id,
    String distributionId,
    String url,
    String title,
    String description,
    CsvwTableSchema tableSchema,
    CsvwDialect dialect,
    @JsonProperty("suppressOutput") Boolean suppressOutput,
    String tableDirection,
    List<Map<String, Object>> notes,    // arbitrary annotation objects
    List<Map<String, Object>> transformations
) {}
