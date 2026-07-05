package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for binding a logical data element to a physical CSV-W column")
public record BindPhysicalColumnRequest(

    @Schema(description = "UUID of the physical column (csvw_columns row) to bind",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull UUID physicalColumnId

) {}
