package com.odin.catalog.shared.models.dcat;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DcatDataService(
    DcatResource resource,
    String endpointUrl,
    String endpointDescription,
    List<String> servesDataset,     // dataset IDs
    String protocol,                // dprod:Protocol IRI
    String securitySchemaType
) {}
