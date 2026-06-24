package com.odin.catalog.ai.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request to run the agentic proposer/reviewer review over one logical model")
public record AgenticReviewRequest(

    @Schema(description = "Dataset UUID whose full DCAT context is given to the reviewer agent",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull UUID datasetId,

    @Schema(description = "Logical model UUID whose elements are reviewed",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull UUID modelId
) {}
