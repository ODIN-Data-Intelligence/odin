package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request body for creating or updating a DCAT catalog")
public record CatalogRequest(

    @Schema(description = "Human-readable title", example = "Enterprise Data Catalog")
    @NotBlank String title,

    @Schema(description = "Description of the catalog's scope and purpose",
        example = "Main catalog for all trading-desk datasets")
    String description,

    @Schema(description = "Homepage URL for the catalog", example = "https://example.com/catalog")
    String homepage,

    @Schema(description = "Owning business domain UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID domainId,

    @Schema(description = "Searchable keywords", example = "[\"trading\", \"finance\"]")
    List<String> keywords,

    @Schema(description = "Thematic categories", example = "[\"Finance\"]")
    List<String> themes,

    @Schema(description = "BCP-47 language codes", example = "[\"en\"]")
    List<String> language,

    @Schema(description = "License identifier or URI", example = "Proprietary")
    String license

) {}
