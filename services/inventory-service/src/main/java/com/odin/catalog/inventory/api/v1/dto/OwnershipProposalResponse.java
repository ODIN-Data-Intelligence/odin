package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "An ownership transfer proposal for a dataset")
public record OwnershipProposalResponse(

    @Schema(description = "Proposal UUID", accessMode = Schema.AccessMode.READ_ONLY)
    UUID id,

    @Schema(description = "Dataset UUID")
    UUID datasetId,

    @Schema(description = "UUID of the proposed new owner")
    UUID proposedOwnerId,

    @Schema(description = "UUID of the user who submitted the proposal")
    UUID proposedById,

    @Schema(description = "Proposal status", example = "PENDING",
        allowableValues = {"PENDING", "APPROVED", "REJECTED"})
    String status,

    @Schema(description = "When the proposal was created", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt,

    @Schema(description = "When the proposal was approved or rejected", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime resolvedAt,

    @Schema(description = "Optional note left by the approver or rejector", example = "Transferring ownership to the trading desk lead")
    String note

) {}
