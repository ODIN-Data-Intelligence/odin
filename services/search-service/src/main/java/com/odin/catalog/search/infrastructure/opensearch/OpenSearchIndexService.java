package com.odin.catalog.search.infrastructure.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.search.api.v1.dto.SearchResponse.FacetBucket;
import com.odin.catalog.search.api.v1.dto.SearchResponse.SearchFacetsDto;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class OpenSearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchIndexService.class);
    private static final String INDEX = "catalog_entities";

    private final OpenSearchClient client;
    private final ObjectMapper objectMapper;

    public void index(CatalogSearchDocument document) {
        try {
            client.index(i -> i
                .index(INDEX)
                .id(document.id())
                .document(document)
            );
        } catch (Exception e) {
            log.error("Failed to index document {}: {}", document.id(), e.getMessage());
        }
    }

    public void delete(String id) {
        try {
            client.delete(d -> d.index(INDEX).id(id));
        } catch (Exception e) {
            log.error("Failed to delete document {}: {}", id, e.getMessage());
        }
    }

    public SearchResult search(String query, String type, String domainId,
                                String lifecycleStatus, String format,
                                Boolean hasLineage, String tenantId, int page, int size,
                                String keyword, String theme, String vocabConcept, String vocab) {
        try {
            List<Query> filters = new ArrayList<>();

            if (tenantId != null) {
                filters.add(Query.of(q -> q.term(t -> t.field("tenantId").value(FieldValue.of(tenantId)))));
            }
            filters.add(Query.of(q -> q.term(t -> t.field("isDeleted").value(FieldValue.of(false)))));

            if (type != null) filters.add(Query.of(q -> q.term(t -> t.field("entityType").value(FieldValue.of(type)))));
            if (domainId != null) filters.add(Query.of(q -> q.term(t -> t.field("domainId").value(FieldValue.of(domainId)))));
            if (lifecycleStatus != null) filters.add(Query.of(q -> q.term(t -> t.field("lifecycleStatus").value(FieldValue.of(lifecycleStatus)))));
            if (format != null) filters.add(Query.of(q -> q.term(t -> t.field("format").value(FieldValue.of(format)))));
            if (hasLineage != null) filters.add(Query.of(q -> q.term(t -> t.field("hasLineage").value(FieldValue.of(hasLineage)))));
            if (keyword != null) filters.add(Query.of(q -> q.term(t -> t.field("keywords.keyword").value(FieldValue.of(keyword)))));
            if (theme != null) filters.add(Query.of(q -> q.term(t -> t.field("themes").value(FieldValue.of(theme)))));
            if (vocabConcept != null) filters.add(Query.of(q -> q.term(t -> t.field("vocabConceptLabels.keyword").value(FieldValue.of(vocabConcept)))));
            if (vocab != null) filters.add(Query.of(q -> q.term(t -> t.field("vocabularyTypes.keyword").value(FieldValue.of(vocab)))));

            Query fullTextQuery = query != null && !query.isBlank()
                ? Query.of(q -> q.multiMatch(m -> m
                    .query(query)
                    .fields(List.of("title^3", "description^2", "keywords", "column_names",
                                    "logical_element_names", "vocab_concept_labels"))
                    .type(TextQueryType.BestFields)
                    .fuzziness("AUTO")))
                : Query.of(q -> q.matchAll(m -> m));

            Query finalQuery = Query.of(q -> q.bool(b -> b
                .must(fullTextQuery)
                .filter(filters)
            ));

            Map<String, Aggregation> aggs = Map.of(
                "entityTypes",      Aggregation.of(a -> a.terms(t -> t.field("entityType").size(10))),
                "formats",          Aggregation.of(a -> a.terms(t -> t.field("format").size(20))),
                "lifecycleStatuses",Aggregation.of(a -> a.terms(t -> t.field("lifecycleStatus").size(10))),
                "vocabularyTypes",  Aggregation.of(a -> a.terms(t -> t.field("vocabularyTypes.keyword").size(10))),
                "fiboConcepts",     Aggregation.of(a -> a.terms(t -> t.field("fiboConcepts.keyword").size(20))),
                "keywords",         Aggregation.of(a -> a.terms(t -> t.field("keywords.keyword").size(30))),
                "themes",           Aggregation.of(a -> a.terms(t -> t.field("themes").size(30))),
                "vocabConcepts",    Aggregation.of(a -> a.terms(t -> t.field("vocabConceptLabels.keyword").size(30)))
            );

            SearchResponse<CatalogSearchDocument> response = client.search(s -> s
                .index(INDEX)
                .query(finalQuery)
                .from(page * size)
                .size(size)
                .highlight(h -> h
                    .fields("title", f -> f)
                    .fields("description", f -> f)
                    .preTags("<em>")
                    .postTags("</em>")
                )
                .aggregations(aggs),
                CatalogSearchDocument.class
            );

            List<CatalogSearchDocument> hits = response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();

            long total = response.hits().total() != null ? response.hits().total().value() : 0L;
            SearchFacetsDto facets = extractFacets(response);
            return new SearchResult(hits, total, page, size, facets);

        } catch (Exception e) {
            log.error("Search failed: {}", e.getMessage());
            return new SearchResult(List.of(), 0, page, size, emptyFacets());
        }
    }

    private List<FacetBucket> bucketsFor(SearchResponse<CatalogSearchDocument> response, String aggName) {
        var agg = response.aggregations().get(aggName);
        if (agg == null) return List.of();
        return agg.sterms().buckets().array().stream()
            .filter(b -> b.docCount() > 0)
            .map(b -> new FacetBucket(b.key(), b.docCount()))
            .toList();
    }

    private SearchFacetsDto extractFacets(SearchResponse<CatalogSearchDocument> response) {
        return new SearchFacetsDto(
            bucketsFor(response, "entityTypes"),
            bucketsFor(response, "formats"),
            bucketsFor(response, "lifecycleStatuses"),
            bucketsFor(response, "vocabularyTypes"),
            bucketsFor(response, "fiboConcepts"),
            bucketsFor(response, "keywords"),
            bucketsFor(response, "themes"),
            bucketsFor(response, "vocabConcepts")
        );
    }

    private SearchFacetsDto emptyFacets() {
        return new SearchFacetsDto(
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );
    }

    public List<String> suggest(String prefix, String tenantId) {
        try {
            List<Query> filters = new ArrayList<>();
            if (tenantId != null) {
                filters.add(Query.of(q -> q.term(t -> t.field("tenantId").value(FieldValue.of(tenantId)))));
            }
            filters.add(Query.of(q -> q.term(t -> t.field("isDeleted").value(FieldValue.of(false)))));

            Query prefixQuery = Query.of(q -> q.matchPhrasePrefix(m -> m
                .field("title")
                .query(prefix)
            ));

            Query finalQuery = Query.of(q -> q.bool(b -> b
                .must(prefixQuery)
                .filter(filters)
            ));

            SearchResponse<CatalogSearchDocument> response = client.search(s -> s
                .index(INDEX)
                .query(finalQuery)
                .size(10)
                .source(src -> src.filter(f -> f.includes("title"))),
                CatalogSearchDocument.class
            );
            return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(CatalogSearchDocument::title)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        } catch (Exception e) {
            log.error("Suggest failed: {}", e.getMessage());
            return List.of();
        }
    }

    public record SearchResult(
        List<CatalogSearchDocument> documents,
        long totalHits,
        int page,
        int size,
        SearchFacetsDto facets
    ) {}
}
