package com.odin.catalog.shared.models.csvw;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsvwTableSchema(
    List<CsvwColumn> columns,
    List<String> primaryKey,
    List<CsvwForeignKey> foreignKeys,
    String aboutUrl,
    String propertyUrl,
    String valueUrl
) {}
