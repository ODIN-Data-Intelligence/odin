package com.odin.catalog.shared.models.events;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HarvestDdlDiscoveredPayload(
    String runId,
    String sourceId,
    String sourceType,
    String objectType,       // VIEW, STORED_PROC, TABLE
    String objectNamespace,  // database.schema
    String objectName,
    String dialect,          // snowflake, teradata, hive, ansi
    String ddl
) {}
