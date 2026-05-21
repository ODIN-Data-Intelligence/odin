package com.odin.catalog.shared.models.csvw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsvwDialect(
    String delimiter,
    String quoteChar,
    @JsonProperty("doubleQuote") Boolean doubleQuote,
    Integer skipRows,
    Integer headerRowCount,
    String lineTerminators,
    String trim,
    @JsonProperty("skipBlankRows") Boolean skipBlankRows,
    Integer skipColumns,
    @JsonProperty("header") Boolean header,
    String commentPrefix
) {}
