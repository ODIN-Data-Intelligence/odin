package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public record IriTranslateBatchResponse(
    @Schema(description = "Map of IRI → preferred label for each requested IRI")
    Map<String, String> translations
) {}
