package com.odin.catalog.policy.api.v1.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EvaluationLogEntry(
    UUID id,
    UUID datasetId,
    String action,
    boolean granted,
    OffsetDateTime createdAt
) {}
