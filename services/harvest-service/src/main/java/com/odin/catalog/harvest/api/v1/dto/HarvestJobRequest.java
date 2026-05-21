package com.odin.catalog.harvest.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for creating a harvest job")
public record HarvestJobRequest(

    @Schema(description = "UUID of the harvest source this job targets", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull UUID sourceId,

    @Schema(description = "Human-readable name for the job", example = "Nightly Glue harvest")
    @NotBlank String name,

    @Schema(description = "Quartz cron expression for scheduled execution. Null for manual-only jobs.",
        example = "0 0 2 * * ?")
    String scheduleCron,

    @Schema(description = "When true, re-harvests all entities even if unchanged. When false, only harvests new or modified entities.",
        example = "false")
    boolean fullRefresh,

    @Schema(description = "Whether this job is active and will run on schedule", example = "true")
    boolean enabled

) {}
