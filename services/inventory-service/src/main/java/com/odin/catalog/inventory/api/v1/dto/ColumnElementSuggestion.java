package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Suggested binding between a physical column and a logical data element")
public record ColumnElementSuggestion(

    @Schema(description = "UUID of the physical column (csvw_columns row)",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID columnId,

    @Schema(description = "Name of the physical column as harvested from the source system",
        example = "trade_amount")
    String columnName,

    @Schema(description = "UUID of the suggested logical data element",
        example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    UUID suggestedElementId,

    @Schema(description = "Business name of the suggested logical data element", example = "Trade Amount")
    String suggestedElementName,

    @Schema(description = "Confidence score for this suggestion (0.0 – 1.0, higher is better)",
        example = "0.91")
    double confidence

) {}
