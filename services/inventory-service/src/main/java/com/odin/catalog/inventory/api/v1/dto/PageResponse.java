package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated response wrapper")
public record PageResponse<T>(

    @Schema(description = "Items on the current page")
    List<T> content,

    @Schema(description = "Zero-based page number", example = "0")
    int page,

    @Schema(description = "Requested page size", example = "20")
    int size,

    @Schema(description = "Total number of matching items across all pages", example = "142")
    long totalElements,

    @Schema(description = "Total number of pages", example = "8")
    int totalPages

) {
    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    public static <T> PageResponse<T> ofSingle(T item) {
        return new PageResponse<>(List.of(item), 0, 1, 1, 1);
    }

    public static <T> PageResponse<T> empty() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0);
    }
}
