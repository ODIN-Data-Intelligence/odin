package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "A dataset change made by the current user")
public record ActivityChangeResponse(

    @Schema(description = "Audit entry UUID")
    UUID id,

    @Schema(description = "Dataset UUID")
    UUID datasetId,

    @Schema(description = "Dataset title for display")
    String datasetTitle,

    @Schema(description = "Event type", example = "UPDATED",
        allowableValues = {"CREATED", "UPDATED", "DELETED", "OWNER_ASSIGNED",
                           "OWNER_TRANSFER_PROPOSED", "OWNER_TRANSFER_APPROVED", "OWNER_TRANSFER_REJECTED"})
    String eventType,

    @Schema(description = "When the change occurred")
    OffsetDateTime createdAt

) {}
