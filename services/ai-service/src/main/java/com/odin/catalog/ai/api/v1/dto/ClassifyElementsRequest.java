package com.odin.catalog.ai.api.v1.dto;

import java.util.List;

public record ClassifyElementsRequest(List<ElementInput> elements) {

    public record ElementInput(
        String elementId,
        String name,
        String label,
        String logicalType,
        String description,
        List<String> vocabConceptIris,
        List<String> vocabConceptLabels
    ) {}
}
