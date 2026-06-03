package com.odin.catalog.ai.api.v1.dto;

import java.util.List;

public record VocabConceptRecommendationRequest(List<ElementVocabInput> elements) {

    public record ElementVocabInput(
        String elementId,
        String name,
        String label,
        String logicalType,
        String description,
        List<String> existingConceptIris,
        List<String> existingConceptLabels,
        List<VocabInfo> availableVocabularies
    ) {}

    public record VocabInfo(
        String prefix,
        String baseIri,
        String name
    ) {}
}
