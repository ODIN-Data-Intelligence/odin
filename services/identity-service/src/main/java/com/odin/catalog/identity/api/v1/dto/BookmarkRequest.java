package com.odin.catalog.identity.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for bookmarking a dataset")
public record BookmarkRequest(
    @Schema(description = "Dataset UUID to bookmark", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull UUID datasetId,

    @Schema(description = "Dataset title (denormalised for display)", example = "Equities EOD Prices")
    @NotBlank String datasetTitle,

    @Schema(description = "Optional collection to add this bookmark to")
    UUID collectionId,

    @Schema(description = "Optional personal note about this dataset")
    String note
) {}
