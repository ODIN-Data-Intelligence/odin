package com.odin.catalog.ai.api.v1.dto;

import java.util.List;

public record PiiRecommendationRequest(List<ElementPiiInput> elements) {

    public record ElementPiiInput(
        String elementId,
        String name,
        String label,
        String logicalType,
        String description,
        List<String> vocabConceptIris,
        List<String> vocabConceptLabels,
        String datasetTitle,
        List<String> datasetKeywords
    ) {}
}
