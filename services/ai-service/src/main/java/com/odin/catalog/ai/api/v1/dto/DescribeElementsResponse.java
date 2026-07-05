package com.odin.catalog.ai.api.v1.dto;

import java.util.List;

public record DescribeElementsResponse(List<ElementResult> results) {

    public record ElementResult(
        String elementId,
        String description,
        String reasoning
    ) {}
}
