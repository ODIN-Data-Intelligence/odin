package com.odin.catalog.ai.api.v1.dto;

import java.util.List;

public record VocabConceptRecommendationResponse(List<ElementVocabResult> results) {

    public record ElementVocabResult(
        String elementId,
        List<ConceptSuggestion> concepts
    ) {}

    public record ConceptSuggestion(
        String conceptIri,
        String conceptLabel,
        String conceptDefinition,
        String matchType,
        String reasoning
    ) {}
}
