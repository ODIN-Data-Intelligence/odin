package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "A single audit log entry for a dataset mutation")
public record DatasetAuditResponse(

    @Schema(description = "Audit log entry UUID", accessMode = Schema.AccessMode.READ_ONLY)
    UUID id,

    @Schema(description = "Dataset UUID this entry belongs to")
    UUID datasetId,

    @Schema(description = "Event type", example = "UPDATED",
        allowableValues = {"CREATED", "UPDATED", "DELETED",
            "OWNER_ASSIGNED", "OWNER_TRANSFER_PROPOSED",
            "OWNER_TRANSFER_APPROVED", "OWNER_TRANSFER_REJECTED"})
    String eventType,

    @Schema(description = "ID of the user who made the change")
    String changedById,

    @Schema(description = "Email of the user who made the change")
    String changedByEmail,

    @Schema(description = "JSON snapshot of the dataset before the change (null for CREATED)")
    String payloadBefore,

    @Schema(description = "JSON snapshot of the dataset after the change (null for DELETED)")
    String payloadAfter,

    @Schema(description = "Timestamp of the audit event", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt

) {}
