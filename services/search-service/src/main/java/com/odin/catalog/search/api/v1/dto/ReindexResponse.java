package com.odin.catalog.search.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a full re-index operation")
public record ReindexResponse(

    @Schema(description = "Number of dataset documents indexed", example = "142")
    int datasetsIndexed,

    @Schema(description = "Number of data product documents indexed", example = "31")
    int dataProductsIndexed,

    @Schema(description = "Number of distribution documents indexed", example = "287")
    int distributionsIndexed

) {}
