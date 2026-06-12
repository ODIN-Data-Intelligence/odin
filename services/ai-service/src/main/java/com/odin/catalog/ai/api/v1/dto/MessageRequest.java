package com.odin.catalog.ai.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(description = "Request body for sending a message to an AI conversation")
public record MessageRequest(

    @Schema(description = "The user's message text",
        example = "What datasets are available for credit risk analysis?")
    @NotBlank String content,

    @Schema(description = "UUID of a single dataset to focus the conversation on (kept for backward compatibility). "
        + "Prefer focusDatasetIds for multi-dataset queries.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    String focusDatasetId,

    @Schema(description = "UUIDs of one or more datasets to include as query context. "
        + "The AI loads physical schema and vocabulary mappings for all listed datasets and derives join hints "
        + "from shared vocabulary concept IRIs. Union with focusDatasetId when both are set.",
        example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"7c9e6679-7425-40de-944b-e07fc1f90ae7\"]")
    List<String> focusDatasetIds

) {}
