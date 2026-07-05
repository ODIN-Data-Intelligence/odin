package com.odin.catalog.shared.models.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.odin.catalog.shared.models.common.NormalizedColumn;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HarvestEntityDiscoveredPayload(
    String runId,
    String sourceId,
    String sourceType,       // dcat_http, aws_glue, snowflake, teradata
    String entityType,       // DATASET, DATA_SERVICE, CATALOG
    String sourceKey,        // unique key within source (e.g., "db.schema.table")
    String sourceUri,        // full URI / connection string identifying the entity
    String title,
    String description,
    String format,
    String mediaType,
    List<String> keywords,
    List<String> themes,
    List<HarvestDistributionPayload> distributions,
    List<NormalizedColumn> columns,
    JsonNode rawPayload
) {}
