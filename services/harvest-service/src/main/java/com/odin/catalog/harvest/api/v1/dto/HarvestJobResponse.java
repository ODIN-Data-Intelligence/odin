package com.odin.catalog.harvest.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Harvest job configuration and state")
public record HarvestJobResponse(

    @Schema(description = "Job UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "UUID of the harvest source this job targets")
    UUID sourceId,

    @Schema(description = "Human-readable job name", example = "Nightly Glue harvest")
    String name,

    @Schema(description = "Quartz cron expression, or null for manual-only jobs", example = "0 0 2 * * ?")
    String scheduleCron,

    @Schema(description = "When true, re-harvests all entities on each run", example = "false")
    boolean fullRefresh,

    @Schema(description = "Whether this job is active and will run on schedule", example = "true")
    boolean enabled,

    OffsetDateTime createdAt,
    OffsetDateTime updatedAt

) {}
