package com.odin.catalog.shared.models.csvw;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsvwForeignKey(
    List<String> columnReference,
    CsvwForeignKeyReference reference
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CsvwForeignKeyReference(
        String resource,            // URL of the referenced table
        List<String> columnReference
    ) {}
}
