package com.odin.catalog.inventory.api.v1.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TermsPolicyDetailResponse(
        UUID id,
        String name,
        String description,
        String status,
        int version,
        OffsetDateTime effectiveFrom,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<TermsClassificationRuleResponse> classificationRules,
        List<TermsRegulationRuleResponse> regulationRules,
        List<TermsRegulationObligationResponse> regulationObligations
) {}
