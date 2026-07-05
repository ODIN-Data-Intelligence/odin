package com.odin.catalog.policy.api.v1.dto;

import java.util.List;
import java.util.UUID;

public record PolicyComponentsResponse(
    UUID datasetId,
    UUID tenantId,
    List<PolicyComponentSummary> components,
    Object assembledPolicy
) {}
