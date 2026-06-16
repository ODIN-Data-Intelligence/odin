package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "A single entry in the unified dataset/model/element activity timeline")
public record DatasetActivityResponse(

    @Schema(description = "Audit log entry UUID", accessMode = Schema.AccessMode.READ_ONLY)
    UUID id,

    @Schema(description = "Scope of the entity this event happened to",
        allowableValues = {"DATASET", "MODEL", "ELEMENT"})
    String scope,

    @Schema(description = "Dataset UUID")
    UUID datasetId,

    @Schema(description = "Logical model UUID, when scope is MODEL or ELEMENT")
    UUID logicalModelId,

    @Schema(description = "Logical element UUID, when scope is ELEMENT")
    UUID logicalElementId,

    @Schema(description = "Display name of the dataset/model/element at the time of the event")
    String entityName,

    @Schema(description = "Event type", example = "UPDATED")
    String eventType,

    @Schema(description = "ID of the user who made the change")
    String changedById,

    @Schema(description = "Email of the user who made the change")
    String changedByEmail,

    @Schema(description = "JSON snapshot before the change")
    String payloadBefore,

    @Schema(description = "JSON snapshot after the change")
    String payloadAfter,

    @Schema(description = "Timestamp of the audit event", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt

) {}
