package com.odin.catalog.ai.api.v1.dto;

import java.util.List;

public record PiiRecommendationResponse(List<ElementPiiResult> results) {

    public record ElementPiiResult(
        String elementId,
        boolean isPersonalInformation,
        boolean isDirectIdentifier,
        String reasoning
    ) {}
}
