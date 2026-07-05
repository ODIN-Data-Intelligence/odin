package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating or updating a user-defined vocabulary")
public record VocabularyRequest(

    @Schema(description = "Human-readable name of the vocabulary", example = "Internal Risk Ontology")
    @NotBlank String name,

    @Schema(description = "Short namespace prefix used in concept IRIs", example = "risk")
    @NotBlank String prefix,

    @Schema(description = "Base IRI for all concepts in this vocabulary",
        example = "https://example.com/ontology/risk#")
    @NotBlank String baseIri,

    @Schema(description = "Vocabulary category",
        allowableValues = {"general", "financial", "healthcare", "geospatial", "custom"},
        example = "custom")
    String vocabularyType,

    @Schema(description = "Description of the vocabulary's scope and concepts")
    String description,

    @Schema(description = "Comma-separated concept hints that guide AI recommendations",
        example = "Risk, Exposure, Counterparty, Limit")
    String conceptHints,

    @Schema(description = "Version string", example = "1.0.0")
    String version,

    @Schema(description = "Homepage or documentation URL", example = "https://example.com/ontology/risk")
    String homepage

) {}
