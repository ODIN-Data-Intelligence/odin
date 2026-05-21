package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating a logical data model for a dataset")
public record LogicalModelRequest(

    @Schema(description = "Name of the logical model — typically reflects the business view it represents",
        example = "Trade Positions Model")
    @NotBlank String name,

    @Schema(description = "Description of what this model represents",
        example = "Semantic business view of the daily trade position dataset")
    String description,

    @Schema(description = "Version string for this model revision", example = "1.0")
    String version

) {}
