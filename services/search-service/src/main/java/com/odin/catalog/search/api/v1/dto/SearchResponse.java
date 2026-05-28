package com.odin.catalog.search.api.v1.dto;

import com.odin.catalog.search.infrastructure.opensearch.CatalogSearchDocument;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Search response with paginated results and aggregated facet counts")
public record SearchResponse(

    @Schema(description = "Search result documents matching the query and filters")
    List<CatalogSearchDocument> results,

    @Schema(description = "Total number of matching documents across all pages", example = "142")
    long totalHits,

    @Schema(description = "Zero-based current page number", example = "0")
    int page,

    @Schema(description = "Requested page size", example = "20")
    int size,

    @Schema(description = "Total number of pages", example = "8")
    int totalPages,

    @Schema(description = "Aggregated facet buckets for filtering — one list per facet dimension")
    SearchFacetsDto facets

) {
    @Schema(description = "A single facet bucket — a distinct value and how many documents match it")
    public record FacetBucket(

        @Schema(description = "The facet value", example = "DATASET")
        String key,

        @Schema(description = "Number of matching documents with this value", example = "47")
        long count

    ) {}

    @Schema(description = "Facet aggregations across all search result dimensions")
    public record SearchFacetsDto(

        @Schema(description = "Document type breakdown (DATASET, DATA_PRODUCT, DISTRIBUTION)")
        List<FacetBucket> entityTypes,

        @Schema(description = "Distribution format breakdown (Parquet, CSV, Snowflake, …)")
        List<FacetBucket> formats,

        @Schema(description = "Data product lifecycle status breakdown")
        List<FacetBucket> lifecycleStatuses,

        @Schema(description = "Vocabulary type breakdown (financial, general, …)")
        List<FacetBucket> vocabularyTypes,

        @Schema(description = "FIBO concept IRI breakdown — filter to datasets annotated with a specific FIBO concept")
        List<FacetBucket> fiboConcepts,

        @Schema(description = "Keyword tag breakdown")
        List<FacetBucket> keywords,

        @Schema(description = "Theme/category breakdown")
        List<FacetBucket> themes,

        @Schema(description = "Vocabulary concept label breakdown (all vocabularies combined)")
        List<FacetBucket> vocabConcepts,

        @Schema(description = "Semantic type breakdown derived from vocabulary concept IRI fragments (e.g. Customer, Loan)")
        List<FacetBucket> semanticTypes

    ) {}
}
