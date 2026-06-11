package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for updating a logical model's status")
public record UpdateModelStatusRequest(

    @Schema(description = "New model status",
        allowableValues = {"draft", "published", "deprecated"},
        example = "published")
    @NotBlank String status

) {}
