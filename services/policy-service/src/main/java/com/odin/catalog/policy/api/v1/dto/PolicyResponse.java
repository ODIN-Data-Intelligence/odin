package com.odin.catalog.policy.api.v1.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PolicyResponse(
    UUID id,
    UUID datasetId,
    UUID tenantId,
    String policyLevel,
    Object policyJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
