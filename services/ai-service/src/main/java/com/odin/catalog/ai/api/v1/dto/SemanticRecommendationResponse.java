package com.odin.catalog.ai.api.v1.dto;

import java.util.List;

public record SemanticRecommendationResponse(
    List<RecommendedType> types,
    String rationale
) {
    public record RecommendedType(
        String type,
        String rationale,
        String vocabularyHint
    ) {}
}
