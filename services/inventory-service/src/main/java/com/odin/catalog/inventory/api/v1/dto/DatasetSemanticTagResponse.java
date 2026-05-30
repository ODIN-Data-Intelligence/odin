package com.odin.catalog.inventory.api.v1.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DatasetSemanticTagResponse(
    UUID id,
    UUID datasetId,
    String type,
    String vocabularyIri,
    OffsetDateTime createdAt
) {}
