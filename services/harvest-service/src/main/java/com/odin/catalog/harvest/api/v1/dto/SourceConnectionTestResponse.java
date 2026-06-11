package com.odin.catalog.harvest.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a harvest source connection test")
public record SourceConnectionTestResponse(

    @Schema(description = "Whether the connection was established successfully", example = "true")
    boolean success,

    @Schema(description = "Human-readable result message", example = "Connection successful")
    String message

) {}
