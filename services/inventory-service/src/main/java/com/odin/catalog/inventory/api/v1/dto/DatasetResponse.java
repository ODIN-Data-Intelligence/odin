package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "DCAT dataset resource as returned by the API")
public record DatasetResponse(

    @Schema(description = "Server-assigned UUID", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "Title of the dataset", example = "Daily Trade Positions")
    String title,

    @Schema(description = "Description of the dataset", example = "End-of-day trade position snapshot")
    String description,

    @Schema(description = "UUID of the parent catalog", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID catalogId,

    @Schema(description = "UUID of the owning business domain", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID domainId,

    @Schema(description = "UUID of the tenant organisation", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID tenantId,

    @Schema(description = "Accrual periodicity (Dublin Core frequency URI)",
        example = "http://purl.org/cld/freq/daily")
    String accrualPeriodicity,

    @Schema(description = "Searchable keywords", example = "[\"trades\", \"positions\", \"risk\"]")
    List<String> keywords,

    @Schema(description = "Thematic categories", example = "[\"Finance\", \"Trading\"]")
    List<String> themes,

    @Schema(description = "BCP-47 language codes", example = "[\"en\"]")
    List<String> language,

    @Schema(description = "License identifier or URI", example = "Proprietary")
    String license,

    @Schema(description = "Dataset version string", example = "2.1.0")
    String version,

    @Schema(description = "Source URI from the harvesting system",
        example = "arn:aws:glue:eu-west-1:123456789012:table/trades/positions")
    String sourceUri,

    @Schema(description = "Whether the dataset has been soft-deleted", accessMode = Schema.AccessMode.READ_ONLY,
        example = "false")
    boolean isDeleted,

    @Schema(description = "Creation timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt,

    @Schema(description = "Last modification timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime updatedAt,

    @Schema(description = "UUID of the data owner", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID ownerId,

    @Schema(description = "Semantic type labels derived from controlled vocabulary mappings on published logical models " +
        "(e.g. 'Customer', 'DebitCardAccount'). Populated only on single-dataset GET responses.",
        example = "[\"Customer\", \"DebitCardAccount\"]")
    List<String> semanticTypes,

    @Schema(description = "Explicit ODRL policy JSON (overrides the derived terms-of-use when set)")
    String hasPolicy

) {}
