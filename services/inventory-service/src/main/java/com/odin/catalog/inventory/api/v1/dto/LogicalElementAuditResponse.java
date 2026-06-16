package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "A single audit log entry for a logical data element mutation")
public record LogicalElementAuditResponse(

    @Schema(description = "Audit log entry UUID", accessMode = Schema.AccessMode.READ_ONLY)
    UUID id,

    @Schema(description = "Logical element UUID this entry belongs to")
    UUID logicalElementId,

    @Schema(description = "Logical model UUID")
    UUID logicalModelId,

    @Schema(description = "Dataset UUID")
    UUID datasetId,

    @Schema(description = "Display name of the element at the time of the event")
    String elementName,

    @Schema(description = "Event type", example = "UPDATED",
        allowableValues = {"UPDATED",
            "CLASSIFICATION_ACCEPTED", "CLASSIFICATION_REJECTED",
            "DESCRIPTION_ACCEPTED", "DESCRIPTION_REJECTED",
            "VOCAB_MAPPING_ADDED", "VOCAB_MAPPING_DELETED",
            "VOCAB_CONCEPTS_ACCEPTED", "VOCAB_CONCEPTS_REJECTED",
            "PII_ACCEPTED", "PII_REJECTED"})
    String eventType,

    @Schema(description = "ID of the user who made the change")
    String changedById,

    @Schema(description = "Email of the user who made the change")
    String changedByEmail,

    @Schema(description = "JSON snapshot of the element before the change")
    String payloadBefore,

    @Schema(description = "JSON snapshot of the element after the change")
    String payloadAfter,

    @Schema(description = "Timestamp of the audit event", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt

) {}
