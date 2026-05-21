package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Logical data model as returned by the API")
public record LogicalModelResponse(

    @Schema(description = "Server-assigned UUID", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "UUID of the parent dataset", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID datasetId,

    @Schema(description = "Name of the logical model", example = "Trade Positions Model")
    String name,

    @Schema(description = "Description of the model", example = "Semantic business view of the daily trade position dataset")
    String description,

    @Schema(description = "Model version", example = "1.0")
    String version,

    @Schema(description = "Publication status of the model",
        allowableValues = {"draft", "published", "deprecated"},
        example = "published")
    String status,

    @Schema(description = "Logical data elements belonging to this model")
    List<LogicalDataElementResponse> elements,

    @Schema(description = "Creation timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt,

    @Schema(description = "Last modification timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime updatedAt

) {}
