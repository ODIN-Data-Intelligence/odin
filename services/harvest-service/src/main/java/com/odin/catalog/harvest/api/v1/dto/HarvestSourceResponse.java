package com.odin.catalog.harvest.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Harvest source as returned by the API")
public record HarvestSourceResponse(

    @Schema(description = "Server-assigned UUID", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "UUID of the owning tenant", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID tenantId,

    @Schema(description = "Human-readable name for this source", example = "Production Glue Catalog")
    String name,

    @Schema(description = "Connector type",
        allowableValues = {"dcat_http", "aws_glue", "snowflake", "teradata"},
        example = "aws_glue")
    String sourceType,

    @Schema(description = "Base URL (DCAT HTTP sources)", example = "https://data.gov/catalog.json")
    String baseUrl,

    @Schema(description = "AWS region (Glue sources)", example = "eu-west-1")
    String region,

    @Schema(description = "Database name (Snowflake/Teradata sources)", example = "TRADING_DW")
    String databaseName,

    @Schema(description = "Schema filter applied during harvest", example = "[\"PUBLIC\", \"RISK\"]")
    List<String> schemaFilter,

    @Schema(description = "Creation timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt

) {}
