package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request body for creating or updating a data product")
public record DataProductRequest(

    @Schema(description = "Human-readable title of the data product", example = "Trade Positions")
    @NotBlank String title,

    @Schema(description = "Detailed description of the data product's purpose and content",
        example = "Daily end-of-day trade position snapshot for the derivatives desk")
    String description,

    @Schema(description = "UUID of the owning business domain", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID domainId,

    @Schema(description = "UUID of the data owner user", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    UUID ownerId,

    @Schema(description = "Lifecycle status of the data product",
        allowableValues = {"Ideation", "Design", "Build", "Deploy", "Consume"},
        example = "Deploy")
    String lifecycleStatus,

    @Schema(description = "Business purpose — why this data product exists",
        example = "Provides risk team with daily position data for VaR calculations")
    String purpose,

    @Schema(description = "Data sensitivity classification",
        allowableValues = {"Public", "Internal", "Confidential", "Restricted"},
        example = "Internal")
    String informationSensitivity,

    @Schema(description = "Searchable keywords", example = "[\"positions\", \"risk\", \"derivatives\"]")
    List<String> keywords,

    @Schema(description = "Thematic categories", example = "[\"Finance\", \"Risk Management\"]")
    List<String> themes,

    @Schema(description = "License identifier", example = "Proprietary")
    String license

) {}
