package com.odin.catalog.shared.models.dprod;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.odin.catalog.shared.models.dcat.DcatResource;

import java.util.List;

/**
 * OMG DPROD DataProduct — extends DCAT resource with governance and lifecycle metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataProduct(
    DcatResource resource,
    DataProductLifecycleStatus lifecycleStatus,
    String ownerId,
    String purpose,
    String informationSensitivity,
    List<DataProductPort> inputPorts,
    List<DataProductPort> outputPorts
) {}
