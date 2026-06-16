package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "A single audit log entry for a logical model mutation")
public record LogicalModelAuditResponse(

    @Schema(description = "Audit log entry UUID", accessMode = Schema.AccessMode.READ_ONLY)
    UUID id,

    @Schema(description = "Logical model UUID this entry belongs to")
    UUID logicalModelId,

    @Schema(description = "Dataset UUID")
    UUID datasetId,

    @Schema(description = "Display name of the model at the time of the event")
    String modelName,

    @Schema(description = "Event type", example = "MODEL_STATUS_CHANGED",
        allowableValues = {"MODEL_CREATED", "MODEL_STATUS_CHANGED", "MODEL_DELETED", "MODEL_AUTO_SCAFFOLDED"})
    String eventType,

    @Schema(description = "ID of the user who made the change")
    String changedById,

    @Schema(description = "Email of the user who made the change")
    String changedByEmail,

    @Schema(description = "JSON snapshot of the model before the change")
    String payloadBefore,

    @Schema(description = "JSON snapshot of the model after the change")
    String payloadAfter,

    @Schema(description = "Timestamp of the audit event", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt

) {}
