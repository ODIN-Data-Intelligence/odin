package com.odin.catalog.harvest.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.odin.catalog.shared.models.common.NormalizedColumn;

import java.util.List;

public record HarvestEntity(
    String sourceKey,
    HarvestEntityType entityType,
    String sourceUri,
    String title,
    String description,
    String format,
    String mediaType,
    List<String> keywords,
    List<String> themes,
    List<HarvestDistribution> distributions,
    List<NormalizedColumn> columns,
    String ddl,           // non-null for VIEWs/stored procs — triggers lineage extraction
    JsonNode rawPayload
) {}
