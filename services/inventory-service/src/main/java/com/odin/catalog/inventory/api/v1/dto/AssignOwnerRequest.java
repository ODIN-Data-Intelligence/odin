package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for assigning a data owner to an unowned dataset")
public record AssignOwnerRequest(

    @NotNull
    @Schema(description = "UUID of the user to assign as owner",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID userId

) {}
