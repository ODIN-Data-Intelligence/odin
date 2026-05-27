package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for creating or updating a logical data element within a model")
public record LogicalDataElementRequest(

    @Schema(description = "Business name of this data element — the concept name, not the physical column name",
        example = "Trade Amount")
    @NotBlank String name,

    @Schema(description = "Human-readable display label", example = "Trade Amount (USD)")
    String label,

    @Schema(description = "Description of what this element represents",
        example = "Notional value of the trade in the settlement currency")
    String description,

    @Schema(description = "Semantic type of this element — independent of the physical SQL type",
        allowableValues = {"MonetaryAmount", "Currency", "Identifier", "Date", "DateTime", "Party",
            "Quantity", "Percentage", "Flag", "Text", "Code", "URI"},
        example = "MonetaryAmount")
    String logicalType,

    @Schema(description = "Display order within the logical model (1-based)", example = "1")
    @NotNull Integer ordinal,

    @Schema(description = "Whether this element must always have a value", example = "true")
    boolean isRequired,

    @Schema(description = "Whether this element is part of the logical key / identifier", example = "false")
    boolean isIdentifier,

    @Schema(description = "Whether null values are permitted", example = "false")
    boolean isNullable,

    @Schema(description = "Data classification override set by the data owner",
        allowableValues = {"PUBLIC", "INTERNAL", "CONFIDENTIAL", "HIGH_CONFIDENTIAL"})
    String classification

) {}
