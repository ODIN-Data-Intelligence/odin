package com.odin.catalog.shared.models.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.odin.catalog.shared.models.dprod.DataProduct;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataProductChangedPayload(
    String changeType,   // CREATED, UPDATED, DELETED, LIFECYCLE_CHANGED
    String dataProductId,
    String domainId,
    String tenantId,
    String previousLifecycleStatus,
    DataProduct dataProduct
) {}
