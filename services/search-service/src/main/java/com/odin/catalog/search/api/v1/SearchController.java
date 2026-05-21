package com.odin.catalog.search.api.v1;

import com.odin.catalog.search.api.v1.dto.SearchResponse;
import com.odin.catalog.search.infrastructure.opensearch.OpenSearchIndexService;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Search", description = "Full-text and faceted search across all catalog entities indexed in OpenSearch")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final OpenSearchIndexService indexService;

    @Operation(summary = "Search catalog entities",
        description = "Full-text search across datasets, data products, and distributions. "
            + "All filter parameters are optional and combinable. "
            + "Results include aggregated facet counts for each filter dimension. "
            + "Vocabulary concept filters (vocabConcept, vocab) match against logical element annotations.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search results with facet aggregations"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping
    public SearchResponse search(
            @Parameter(description = "Free-text search query — matches title, description, keywords, and logical element names",
                example = "trade positions")
            @RequestParam(required = false) String q,

            @Parameter(description = "Entity type filter",
                schema = @Schema(allowableValues = {"DATASET", "DATA_PRODUCT", "DISTRIBUTION"}))
            @RequestParam(required = false) String type,

            @Parameter(description = "Domain UUID filter", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestParam(required = false) String domainId,

            @Parameter(description = "Data product lifecycle status filter",
                schema = @Schema(allowableValues = {"Ideation", "Design", "Build", "Deploy", "Consume"}))
            @RequestParam(required = false) String lifecycleStatus,

            @Parameter(description = "Distribution format filter (e.g. Parquet, CSV, Snowflake)", example = "Parquet")
            @RequestParam(required = false) String format,

            @Parameter(description = "When true, returns only entities that have lineage data in the graph", example = "true")
            @RequestParam(required = false) Boolean hasLineage,

            @Parameter(description = "Keyword tag filter (exact match against the keywords array)", example = "risk")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Theme/category filter", example = "Finance")
            @RequestParam(required = false) String theme,

            @Parameter(description = "Vocabulary concept IRI filter — returns entities where any logical element is mapped to this concept",
                example = "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount")
            @RequestParam(required = false) String vocabConcept,

            @Parameter(description = "Vocabulary name filter (e.g. 'FIBO FND', 'schema.org')", example = "FIBO FND")
            @RequestParam(required = false) String vocab,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        String tenantId = TenantContextHolder.get();
        var result = indexService.search(q, type, domainId, lifecycleStatus, format, hasLineage,
                tenantId, page, size, keyword, theme, vocabConcept, vocab);

        return new SearchResponse(
            result.documents(),
            result.totalHits(),
            page,
            size,
            (int) Math.ceil((double) result.totalHits() / size),
            result.facets()
        );
    }

    @Operation(summary = "Autocomplete suggestions",
        description = "Returns up to 10 completion suggestions for the given query prefix. "
            + "Used to drive the search bar autocomplete dropdown.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of suggestion strings"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/suggest")
    public List<String> suggest(
            @Parameter(description = "Query prefix to complete", example = "trade pos")
            @RequestParam String q) {
        String tenantId = TenantContextHolder.get();
        return indexService.suggest(q, tenantId);
    }
}
