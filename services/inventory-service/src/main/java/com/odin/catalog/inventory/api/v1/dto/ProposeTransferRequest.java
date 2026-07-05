package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for proposing an ownership transfer to another user")
public record ProposeTransferRequest(

    @NotNull
    @Schema(description = "UUID of the proposed new owner",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID proposedOwnerId

) {}
