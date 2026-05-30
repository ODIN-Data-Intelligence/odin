package com.odin.catalog.inventory.api.v1.dto;

import jakarta.validation.constraints.NotBlank;

public record DatasetSemanticTagRequest(
    @NotBlank String type,
    String vocabularyIri
) {}
