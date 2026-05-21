package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request body for creating or updating a DCAT dataset")
public record DatasetRequest(

    @Schema(description = "Human-readable title of the dataset", example = "Daily Trade Positions")
    @NotBlank String title,

    @Schema(description = "Detailed description of the dataset's content and purpose",
        example = "End-of-day trade position snapshot including notional, currency, and counterparty fields")
    String description,

    @Schema(description = "UUID of the parent catalog", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID catalogId,

    @Schema(description = "UUID of the owning business domain", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID domainId,

    @Schema(description = "Dublin Core accrual periodicity (how often data is updated)",
        example = "http://purl.org/cld/freq/daily")
    String accrualPeriodicity,

    @Schema(description = "Searchable keywords", example = "[\"trades\", \"positions\", \"risk\"]")
    List<String> keywords,

    @Schema(description = "Thematic categories", example = "[\"Finance\", \"Trading\"]")
    List<String> themes,

    @Schema(description = "BCP-47 language codes for the dataset content", example = "[\"en\"]")
    List<String> language,

    @Schema(description = "License identifier or URI", example = "Proprietary")
    String license,

    @Schema(description = "Dataset version string", example = "2.1.0")
    String version,

    @Schema(description = "Source URI used to identify this dataset when harvested from an external system",
        example = "arn:aws:glue:eu-west-1:123456789012:table/trades/positions")
    String sourceUri

) {}
