package com.odin.catalog.policy.api.v1.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PolicyComponentSummary(
    UUID pieceId,
    String pieceType,
    String dimensionKey,
    String label,
    String policyLevel,
    Object policyFragment,
    OffsetDateTime appliedAt
) {}
