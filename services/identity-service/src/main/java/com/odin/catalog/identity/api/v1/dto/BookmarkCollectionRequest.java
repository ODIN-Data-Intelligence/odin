package com.odin.catalog.identity.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating or updating a bookmark collection")
public record BookmarkCollectionRequest(
    @Schema(description = "Collection name", example = "Finance Datasets")
    @NotBlank String name,

    @Schema(description = "Optional description", example = "Datasets used for financial reporting")
    String description
) {}
