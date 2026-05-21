package com.odin.catalog.shared.models.dcat;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DcatCatalogRecord(
    DcatResource resource,
    String primaryTopicId,          // the described resource ID
    String listingDate,             // ISO-8601
    String modificationDate,        // ISO-8601
    String harvestSource            // source catalog URI
) {}
