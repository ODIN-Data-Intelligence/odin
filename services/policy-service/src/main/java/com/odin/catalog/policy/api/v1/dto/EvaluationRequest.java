package com.odin.catalog.policy.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public record EvaluationRequest(

    @Schema(description = "Data variables injected into B1/B2 policy placeholders",
        example = "{\"callerRole\": \"DATA_OWNER\", \"callerId\": \"user-uuid\"}")
    Map<String, Object> M,

    @Schema(description = "Coded function implementations for C-Level policies (advanced)",
        example = "{}")
    Map<String, Object> F
) {}
