package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(description = "A single column in a CSV-W physical schema")
public record CsvwColumnRequest(

    @Schema(description = "Column name as it appears in the source", example = "customer_id")
    @NotBlank String name,

    @Schema(description = "CSVW datatype", example = "string")
    String datatype,

    @Schema(description = "Column description")
    String description,

    @Schema(description = "Whether the column is required (non-nullable)", example = "true")
    Boolean required,

    @Schema(description = "Alternate titles for this column")
    List<String> titles,

    @Schema(description = "CSVW propertyUrl — maps this column to an RDF property IRI",
        example = "https://schema.org/identifier")
    String propertyUrl,

    @Schema(description = "CSVW virtual column flag — column not present in the source data")
    Boolean virtual,

    @Schema(description = "CSVW suppressOutput flag")
    Boolean suppressOutput,

    @Schema(description = "Default language tag for string values", example = "en")
    String lang,

    @Schema(description = "Default value if column is absent or null")
    String defaultValue,

    @Schema(description = "CSVW valueUrl — maps column value to an IRI template")
    String valueUrl,

    @Schema(description = "CSVW aboutUrl — subject IRI template for rows")
    String aboutUrl

) {}
