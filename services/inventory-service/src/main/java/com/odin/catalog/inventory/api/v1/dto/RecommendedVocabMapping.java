package com.odin.catalog.inventory.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI-recommended vocabulary concept mapping pending review")
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecommendedVocabMapping(

    @Schema(description = "Full IRI of the suggested concept",
        example = "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount")
    String conceptIri,

    @Schema(description = "Human-readable label for the concept", example = "MonetaryAmount")
    String conceptLabel,

    @Schema(description = "Definition of the concept from the vocabulary")
    String conceptDefinition,

    @Schema(description = "SKOS match type",
        allowableValues = {"exactMatch", "closeMatch", "relatedMatch", "broadMatch", "narrowMatch"},
        example = "exactMatch")
    String matchType,

    @Schema(description = "One-sentence reasoning from the AI for this suggestion")
    String reasoning

) {}
