package com.odin.catalog.inventory.api.v1.dto;

import java.time.OffsetDateTime;

public record BulkRecommendationJobResponse(
    String jobId,
    String modelId,
    String status,        // PENDING | RUNNING | COMPLETED | FAILED
    OffsetDateTime createdAt,
    OffsetDateTime completedAt,
    String error
) {}
