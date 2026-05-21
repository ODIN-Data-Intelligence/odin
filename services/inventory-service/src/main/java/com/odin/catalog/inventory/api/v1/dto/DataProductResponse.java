package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Data product resource as returned by the API")
public record DataProductResponse(

    @Schema(description = "Server-assigned UUID", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "Title of the data product", example = "Trade Positions")
    String title,

    @Schema(description = "Detailed description", example = "Daily end-of-day trade position snapshot")
    String description,

    @Schema(description = "UUID of the owning business domain", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID domainId,

    @Schema(description = "UUID of the tenant organisation", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID tenantId,

    @Schema(description = "UUID of the data owner user", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    UUID ownerId,

    @Schema(description = "Current lifecycle status",
        allowableValues = {"Ideation", "Design", "Build", "Deploy", "Consume"},
        example = "Deploy")
    String lifecycleStatus,

    @Schema(description = "Business purpose", example = "Provides risk team with daily position data for VaR calculations")
    String purpose,

    @Schema(description = "Data sensitivity classification",
        allowableValues = {"Public", "Internal", "Confidential", "Restricted"},
        example = "Internal")
    String informationSensitivity,

    @Schema(description = "Searchable keywords", example = "[\"positions\", \"risk\", \"derivatives\"]")
    List<String> keywords,

    @Schema(description = "Thematic categories", example = "[\"Finance\", \"Risk Management\"]")
    List<String> themes,

    @Schema(description = "Creation timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt,

    @Schema(description = "Last modification timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime updatedAt

) {}
