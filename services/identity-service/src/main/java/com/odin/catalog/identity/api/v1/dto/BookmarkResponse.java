package com.odin.catalog.identity.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "A bookmarked dataset")
public record BookmarkResponse(
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) UUID id,
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) UUID tenantId,
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) UUID userId,
    UUID datasetId,
    String datasetTitle,
    UUID collectionId,
    String note,
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) OffsetDateTime createdAt,
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) OffsetDateTime updatedAt
) {}
