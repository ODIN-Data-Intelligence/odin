package com.odin.catalog.inventory.api.v1.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TermsPolicySetResponse(
        UUID id,
        String name,
        String description,
        String status,
        int version,
        OffsetDateTime effectiveFrom,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
