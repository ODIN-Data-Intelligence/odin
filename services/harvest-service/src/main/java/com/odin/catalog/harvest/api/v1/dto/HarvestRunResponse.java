package com.odin.catalog.harvest.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Harvest run execution record")
public record HarvestRunResponse(

    @Schema(description = "Run UUID")
    UUID id,

    @Schema(description = "UUID of the job that produced this run")
    UUID jobId,

    @Schema(description = "UUID of the harvest source")
    UUID sourceId,

    @Schema(description = "Run status", allowableValues = {"pending", "running", "completed", "failed"})
    String status,

    @Schema(description = "What triggered this run — 'api', 'schedule', etc.")
    String triggeredBy,

    OffsetDateTime startedAt,
    OffsetDateTime completedAt,

    Integer entitiesDiscovered,
    Integer entitiesCreated,
    Integer entitiesUpdated,
    Integer entitiesFailed,

    String errorMessage,

    boolean fullRefresh,

    OffsetDateTime createdAt

) {}
