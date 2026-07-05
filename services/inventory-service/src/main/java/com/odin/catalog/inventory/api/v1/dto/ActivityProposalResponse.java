package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "An ownership proposal the current user is involved in as proposer or nominee")
public record ActivityProposalResponse(

    @Schema(description = "Proposal UUID")
    UUID id,

    @Schema(description = "Dataset UUID")
    UUID datasetId,

    @Schema(description = "Dataset title for display")
    String datasetTitle,

    @Schema(description = "UUID of the proposed new owner")
    UUID proposedOwnerId,

    @Schema(description = "UUID of the user who submitted the proposal")
    UUID proposedById,

    @Schema(description = "Whether the current user is the PROPOSER or NOMINEE", example = "PROPOSER",
        allowableValues = {"PROPOSER", "NOMINEE"})
    String role,

    @Schema(description = "Proposal status", allowableValues = {"PENDING", "APPROVED", "REJECTED"})
    String status,

    @Schema(description = "When the proposal was created")
    OffsetDateTime createdAt,

    @Schema(description = "When the proposal was resolved")
    OffsetDateTime resolvedAt,

    @Schema(description = "Note left by the approver or rejector")
    String note

) {}
