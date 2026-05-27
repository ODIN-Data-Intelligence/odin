package com.odin.catalog.search.infrastructure.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class OpenSearchIndexServiceTest {

    // OpenSearchClient overloads (index, delete, search) are generic and ambiguous to Mockito's
    // any() at compile time. We test resilience via the service's own catch-all handlers,
    // which fire when Mockito's default null return triggers an NPE internally.
    @Mock OpenSearchClient client;
    @Mock ObjectMapper objectMapper;

    OpenSearchIndexService service;

    @BeforeEach
    void setUp() {
        service = new OpenSearchIndexService(client, objectMapper);
    }

    // ── index ─────────────────────────────────────────────────────────────

    @Test
    void index_internalFailure_logsAndDoesNotPropagate() {
        // client.index() returns null (Mockito default) → NPE caught by service
        assertThatCode(() -> service.index(doc("ds-1", "DATASET", "Orders")))
            .doesNotThrowAnyException();
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_internalFailure_logsAndDoesNotPropagate() {
        // client.delete() returns null → NPE caught by service
        assertThatCode(() -> service.delete("ds-1"))
            .doesNotThrowAnyException();
    }

    // ── search ────────────────────────────────────────────────────────────

    @Test
    void search_internalFailure_returnsEmptyResultWithEmptyFacets() {
        // client.search() returns null → NPE caught by service → empty result
        var result = service.search("orders", null, null, null, null,
                null, "tenant-1", 0, 20, null, null, null, null, null);

        assertThat(result.documents()).isEmpty();
        assertThat(result.totalHits()).isZero();
        assertThat(result.facets()).isNotNull();
        assertThat(result.facets().entityTypes()).isEmpty();
        assertThat(result.facets().keywords()).isEmpty();
        assertThat(result.facets().vocabConcepts()).isEmpty();
    }

    @Test
    void search_nullQueryAndNoFilters_gracefullyReturnsEmpty() {
        var result = service.search(null, null, null, null, null,
                null, null, 0, 10, null, null, null, null, null);

        assertThat(result.documents()).isEmpty();
        assertThat(result.facets()).isNotNull();
    }

    @Test
    void search_allFiltersProvided_gracefullyReturnsEmpty() {
        var result = service.search("q", "DATASET", "domain-1", "Consume", "CSV",
                true, "tenant-1", 0, 10, "risk", "Finance", "Monetary Amount", "financial", "Customer");

        assertThat(result.documents()).isEmpty();
    }

    // ── suggest ───────────────────────────────────────────────────────────

    @Test
    void suggest_internalFailure_returnsEmptyList() {
        List<String> result = service.suggest("ord", "tenant-1");

        assertThat(result).isEmpty();
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private CatalogSearchDocument doc(String id, String type, String title) {
        return new CatalogSearchDocument(
            id, "tenant-1", type, title, null,
            List.of(), List.of(), null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            false, false, false, 0,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), null
        );
    }
}
