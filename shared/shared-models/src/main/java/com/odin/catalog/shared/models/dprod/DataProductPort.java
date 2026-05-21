package com.odin.catalog.shared.models.dprod;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataProductPort(
    String id,
    String dataProductId,
    DataProductPortType portType,
    String dataServiceId,
    String datasetId,
    String distributionId
) {}
