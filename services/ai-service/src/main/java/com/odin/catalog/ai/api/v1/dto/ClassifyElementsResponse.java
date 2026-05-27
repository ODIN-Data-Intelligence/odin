package com.odin.catalog.ai.api.v1.dto;

import java.util.List;

public record ClassifyElementsResponse(List<ElementResult> results) {

    public record ElementResult(
        String elementId,
        String classification,
        String reasoning
    ) {}
}
