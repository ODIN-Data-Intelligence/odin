package com.odin.catalog.shared.models.csvw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * W3C CSV-W TableGroup — top-level descriptor for a set of related CSV tables.
 * Serialised with {@code @context: "http://www.w3.org/ns/csvw"}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsvwTableGroup(
    @JsonProperty("@context") String context,
    List<CsvwTable> tables,
    CsvwDialect dialect,
    List<Map<String, Object>> notes,
    List<Map<String, Object>> transformations
) {
    public CsvwTableGroup {
        if (context == null) context = "http://www.w3.org/ns/csvw";
    }
}
