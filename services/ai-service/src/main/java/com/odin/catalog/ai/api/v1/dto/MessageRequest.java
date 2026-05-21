package com.odin.catalog.ai.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for sending a message to an AI conversation")
public record MessageRequest(

    @Schema(description = "The user's message text",
        example = "What datasets are available for credit risk analysis?")
    @NotBlank String content,

    @Schema(description = "Optional UUID of a dataset to focus the conversation on. "
        + "When provided, the AI pre-loads the dataset's title, description, and logical model as context before answering.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    String focusDatasetId

) {}
