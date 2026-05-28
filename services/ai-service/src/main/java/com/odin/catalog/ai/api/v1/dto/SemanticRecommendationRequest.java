package com.odin.catalog.ai.api.v1.dto;

import java.util.List;

public record SemanticRecommendationRequest(
    String datasetId,
    String title,
    String description,
    List<String> keywords,
    List<String> themes,
    List<String> elementNames,
    List<String> logicalTypes,
    List<String> currentVocabLabels,
    List<String> currentVocabIris
) {}
