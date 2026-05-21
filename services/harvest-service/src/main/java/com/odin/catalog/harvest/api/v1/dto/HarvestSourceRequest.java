package com.odin.catalog.harvest.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(description = "Request body for creating or updating a harvest source")
public record HarvestSourceRequest(

    @Schema(description = "Human-readable name for this source", example = "Production Glue Catalog")
    @NotBlank String name,

    @Schema(description = "Connector type for this source",
        allowableValues = {"dcat_http", "aws_glue", "snowflake", "teradata"},
        example = "aws_glue")
    @NotBlank String sourceType,

    @Schema(description = "Base URL for DCAT HTTP sources", example = "https://data.gov/catalog.json")
    String baseUrl,

    @Schema(description = "AWS region for Glue sources", example = "eu-west-1")
    String region,

    @Schema(description = "Database/warehouse name for Snowflake or Teradata sources", example = "TRADING_DW")
    String databaseName,

    @Schema(description = "Schema name filter — only harvest tables from these schemas",
        example = "[\"PUBLIC\", \"RISK\"]")
    List<String> schemaFilter,

    @Schema(description = "Reference to the credential stored in the secrets manager", example = "arn:aws:secretsmanager:eu-west-1:123:secret:glue-creds")
    String credentialRef

) {}
