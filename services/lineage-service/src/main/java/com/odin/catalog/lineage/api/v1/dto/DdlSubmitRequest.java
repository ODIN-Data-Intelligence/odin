package com.odin.catalog.lineage.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for submitting DDL for lineage extraction")
public record DdlSubmitRequest(

    @Schema(description = "DDL statement to parse (CREATE VIEW … AS SELECT or CREATE TABLE … AS SELECT)",
        example = "CREATE VIEW risk.daily_pnl AS SELECT t.trade_id, t.pnl FROM trades.positions t")
    @NotBlank String ddl,

    @Schema(description = "OpenLineage namespace for the output dataset",
        example = "snowflake://trading_dw")
    String namespace,

    @Schema(description = "Explicit output dataset name. If omitted, extracted from the DDL object name.",
        example = "daily_pnl")
    String outputName,

    @Schema(description = "SQL dialect for parsing",
        allowableValues = {"ANSI", "SNOWFLAKE", "TERADATA", "HIVE"},
        example = "ANSI")
    String dialect

) {}
