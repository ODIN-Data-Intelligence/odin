package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for transitioning a data product's lifecycle status")
public record LifecycleTransitionRequest(

    @Schema(description = "Target lifecycle status",
        allowableValues = {"Ideation", "Design", "Build", "Deploy", "Consume"},
        example = "Deploy")
    @NotBlank String status

) {}
