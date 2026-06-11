package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating or updating a DCAT Distribution")
public record DistributionRequest(

    @Schema(description = "Human-readable title", example = "Parquet snapshot")
    @NotBlank String title,

    @Schema(description = "Description of this distribution")
    String description,

    @Schema(description = "URL to access the distribution", example = "https://api.example.com/positions")
    String accessUrl,

    @Schema(description = "Direct download URL", example = "s3://bucket/positions/2024-01-01.parquet")
    String downloadUrl,

    @Schema(description = "IANA media type", example = "application/parquet")
    String mediaType,

    @Schema(description = "Format label", example = "Parquet")
    String format,

    @Schema(description = "File size in bytes", example = "1048576")
    Long byteSize,

    @Schema(description = "Checksum algorithm", example = "SHA-256")
    String checksumAlgorithm,

    @Schema(description = "Checksum value")
    String checksumValue,

    @Schema(description = "Compression format", example = "application/gzip")
    String compressFormat,

    @Schema(description = "Package format")
    String packageFormat,

    @Schema(description = "DCAT availability status",
        allowableValues = {"available", "temporary", "experimental", "stable"},
        example = "stable")
    String availability,

    @Schema(description = "Database name for direct-connect distributions", example = "prod_warehouse")
    String databaseName,

    @Schema(description = "Schema name", example = "finance")
    String schemaName,

    @Schema(description = "Table or view name", example = "positions")
    String tableName

) {}
