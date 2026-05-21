package com.odin.catalog.shared.models.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.odin.catalog.shared.models.dcat.DcatDataset;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetChangedPayload(
    String changeType,   // CREATED, UPDATED, DELETED
    String datasetId,
    String catalogId,
    String domainId,
    String tenantId,
    DcatDataset dataset
) {}
