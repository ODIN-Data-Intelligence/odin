package com.odin.catalog.shared.models.events;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HarvestDistributionPayload(
    String title,
    String downloadUrl,
    String accessUrl,
    String format,
    String mediaType
) {}
