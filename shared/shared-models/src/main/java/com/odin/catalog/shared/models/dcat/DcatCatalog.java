package com.odin.catalog.shared.models.dcat;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DcatCatalog(
    DcatResource resource,
    String homepage,
    List<String> hasPart,           // child catalog IDs
    List<DcatDataset> datasets
) {}
