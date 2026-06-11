package com.odin.catalog.search.application;

import com.odin.catalog.search.api.v1.dto.ReindexResponse;
import com.odin.catalog.search.infrastructure.opensearch.OpenSearchIndexService;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReindexServiceTest {

    static final String BASE = "http://localhost:9999";

    @Mock OpenSearchIndexService indexService;
    @Mock RestTemplate restTemplate;

    ReindexService service;

    @BeforeEach
    void setUp() {
        service = new ReindexService(indexService);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "inventoryServiceUrl", BASE);
        TenantContextHolder.set("tenant-1");

        // Default: data products returns empty
        stubDataProductsEmpty();
    }

    // ── reindex basic ─────────────────────────────────────────────────────

    @Test
    void reindex_nullBody_returnsZeroCounts() {
        when(restTemplate.exchange(
            contains("/api/v1/datasets?page=0"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(null));

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isZero();
        assertThat(result.distributionsIndexed()).isZero();
    }

    @Test
    void reindex_emptyContent_returnsZeroCounts() {
        stubDatasetsPage(List.of(), true);

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isZero();
    }

    @Test
    void reindex_pageExceptionBreaks() {
        when(restTemplate.exchange(
            contains("/api/v1/datasets?page=0"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenThrow(new RuntimeException("connection refused"));

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isZero();
        verify(indexService, never()).index(any());
    }

    @Test
    void reindex_datasetProcessingException_skipsAndContinues() {
        stubDatasetsPage(List.of(dataset("ds-1", "Trade Positions")), true);
        stubLogicalModels("ds-1", List.of());
        stubDistributions("ds-1", List.of());
        doThrow(new RuntimeException("index failed")).when(indexService).index(any());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isZero();
    }

    @Test
    void reindex_lastPageTrue_stopsLoop() {
        stubDatasetsPage(List.of(dataset("ds-1", "T1")), true);
        stubLogicalModels("ds-1", List.of());
        stubDistributions("ds-1", List.of());

        service.reindex();

        verify(restTemplate, times(1)).exchange(
            contains("/api/v1/datasets?page=0"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class));
    }

    // ── enrichment path ───────────────────────────────────────────────────

    @Test
    void reindex_withLogicalModelAndElements_enrichesDataset() {
        stubDatasetsPage(List.of(dataset("ds-1", "Trade Data")), true);
        stubLogicalModels("ds-1", List.of(Map.of("id", "lm-1")));
        stubElements("lm-1", List.of(
            Map.of("id", "elem-1", "name", "trade_id", "logicalType", "String")));
        stubVocabMappings("elem-1", List.of(
            Map.of("conceptIri", "https://fibo.org/Trade", "conceptLabel", "Trade", "matchType", "exactMatch")));
        stubDistributions("ds-1", List.of());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
        verify(indexService, atLeastOnce()).index(any());
    }

    @Test
    void reindex_withFiboVocabIri_classifiedAsFinancial() {
        stubDatasetsPage(List.of(dataset("ds-1", "Trade Data")), true);
        stubLogicalModels("ds-1", List.of(Map.of("id", "lm-1")));
        stubElements("lm-1", List.of(Map.of("id", "elem-1", "name", "notional", "logicalType", "Decimal")));
        stubVocabMappings("elem-1", List.of(
            Map.of("conceptIri", "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/MonetaryAmount",
                "conceptLabel", "MonetaryAmount", "matchType", "closeMatch")));
        stubDistributions("ds-1", List.of());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
    }

    @Test
    void reindex_withFragmentStyleVocabIri_extractsFragment() {
        stubDatasetsPage(List.of(dataset("ds-1", "Customer Data")), true);
        stubLogicalModels("ds-1", List.of(Map.of("id", "lm-1")));
        stubElements("lm-1", List.of(Map.of("id", "elem-1", "name", "email")));
        stubVocabMappings("elem-1", List.of(
            Map.of("conceptIri", "https://w3id.org/dpv/dpv-pd#EmailAddress",
                "conceptLabel", "EmailAddress", "matchType", "exactMatch")));
        stubDistributions("ds-1", List.of());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
    }

    @Test
    void reindex_withRelatedMatchVocab_doesNotExtractFragment() {
        stubDatasetsPage(List.of(dataset("ds-1", "Some Data")), true);
        stubLogicalModels("ds-1", List.of(Map.of("id", "lm-1")));
        stubElements("lm-1", List.of(Map.of("id", "elem-1", "name", "ref_code")));
        stubVocabMappings("elem-1", List.of(
            Map.of("conceptIri", "https://schema.org/Thing",
                "conceptLabel", "Thing", "matchType", "relatedMatch")));
        stubDistributions("ds-1", List.of());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
    }

    @Test
    void reindex_vocabMappingWithNullIriAndNullLabel_skipsGracefully() {
        stubDatasetsPage(List.of(dataset("ds-1", "Test")), true);
        stubLogicalModels("ds-1", List.of(Map.of("id", "lm-1")));
        stubElements("lm-1", List.of(Map.of("id", "elem-1", "name", "field")));
        stubVocabMappings("elem-1", List.of(Map.of()));
        stubDistributions("ds-1", List.of());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
    }

    @Test
    void reindex_vocabMappingsNullBody_skipsGracefully() {
        stubDatasetsPage(List.of(dataset("ds-1", "Test")), true);
        stubLogicalModels("ds-1", List.of(Map.of("id", "lm-1")));
        stubElements("lm-1", List.of(Map.of("id", "elem-1", "name", "field")));
        when(restTemplate.exchange(
            contains("/vocab-mappings"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(null));
        stubDistributions("ds-1", List.of());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
    }

    @Test
    void reindex_elementsNullBody_skipsEnrichment() {
        stubDatasetsPage(List.of(dataset("ds-1", "Test")), true);
        stubLogicalModels("ds-1", List.of(Map.of("id", "lm-1")));
        when(restTemplate.exchange(
            contains("/logical-models/lm-1/elements"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(null));
        stubDistributions("ds-1", List.of());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
    }

    @Test
    void reindex_elementsFetchThrows_skipsEnrichment() {
        stubDatasetsPage(List.of(dataset("ds-1", "Test")), true);
        stubLogicalModels("ds-1", List.of(Map.of("id", "lm-1")));
        when(restTemplate.exchange(
            contains("/logical-models/lm-1/elements"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenThrow(new RuntimeException("elements fetch failed"));
        stubDistributions("ds-1", List.of());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
    }

    @Test
    void reindex_vocabMappingsFetchThrows_skipsGracefully() {
        stubDatasetsPage(List.of(dataset("ds-1", "Test")), true);
        stubLogicalModels("ds-1", List.of(Map.of("id", "lm-1")));
        stubElements("lm-1", List.of(Map.of("id", "elem-1", "name", "field")));
        when(restTemplate.exchange(
            contains("/vocab-mappings"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenThrow(new RuntimeException("vocab fetch failed"));
        stubDistributions("ds-1", List.of());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
    }

    @Test
    void reindex_elementWithNullNameAndNullLogicalType_skipsFieldAddition() {
        stubDatasetsPage(List.of(dataset("ds-1", "Test")), true);
        stubLogicalModels("ds-1", List.of(Map.of("id", "lm-1")));
        stubElements("lm-1", List.of(Map.of("id", "elem-1")));
        stubVocabMappings("elem-1", List.of());
        stubDistributions("ds-1", List.of());

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
    }

    // ── distributions ─────────────────────────────────────────────────────

    @Test
    void reindex_withDistributions_indexesThem() {
        stubDatasetsPage(List.of(dataset("ds-1", "FX Data")), true);
        stubLogicalModels("ds-1", List.of());
        Map<String, Object> dist = Map.of(
            "id", "dist-1", "title", "CSV Distribution",
            "mediaType", "text/csv", "format", "CSV");
        stubDistributions("ds-1", List.of(dist));

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
        verify(indexService, atLeast(2)).index(any()); // dataset + distribution
    }

    @Test
    void reindex_distributionsNullBody_skipsDistributions() {
        stubDatasetsPage(List.of(dataset("ds-1", "Test")), true);
        stubLogicalModels("ds-1", List.of());
        when(restTemplate.exchange(
            contains("/api/v1/datasets/ds-1/distributions"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(null));

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
        assertThat(result.distributionsIndexed()).isZero();
    }

    @Test
    void reindex_distributionsFetchThrows_skipsGracefully() {
        stubDatasetsPage(List.of(dataset("ds-1", "Test")), true);
        stubLogicalModels("ds-1", List.of());
        when(restTemplate.exchange(
            contains("/api/v1/datasets/ds-1/distributions"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenThrow(new RuntimeException("distributions failed"));

        ReindexResponse result = service.reindex();

        assertThat(result.datasetsIndexed()).isEqualTo(1);
    }

    // ── data products ─────────────────────────────────────────────────────

    @Test
    void reindex_withDataProducts_indexesDataProducts() {
        stubDatasetsPage(List.of(), true);
        reset(restTemplate);
        stubDataProductsWithContent(List.of(dataProduct("dp-1", "Customer Product")));

        ReindexResponse result = service.reindex();

        assertThat(result.dataProductsIndexed()).isEqualTo(1);
        verify(indexService, times(1)).index(any());
    }

    @Test
    void reindex_dataProductsNullBody_returnsZero() {
        stubDatasetsPage(List.of(), true);
        reset(restTemplate);
        when(restTemplate.exchange(
            contains("/api/v1/data-products"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(null));

        ReindexResponse result = service.reindex();

        assertThat(result.dataProductsIndexed()).isZero();
    }

    @Test
    void reindex_dataProductsExceptionLogged_doesNotPropagate() {
        stubDatasetsPage(List.of(), true);
        reset(restTemplate);
        when(restTemplate.exchange(
            contains("/api/v1/data-products"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenThrow(new RuntimeException("dp fetch failed"));

        ReindexResponse result = service.reindex();

        assertThat(result.dataProductsIndexed()).isZero();
    }

    @Test
    void reindex_dataProductWithNullKeywordsAndThemes_usesEmptyLists() {
        stubDatasetsPage(List.of(), true);
        reset(restTemplate);
        Map<String, Object> dp = Map.of("id", "dp-1", "title", "Product A", "isDeleted", false);
        stubDataProductsWithContent(List.of(dp));

        ReindexResponse result = service.reindex();

        assertThat(result.dataProductsIndexed()).isEqualTo(1);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private void stubDatasetsPage(List<Map<String, Object>> content, boolean last) {
        Map<String, Object> page = new java.util.HashMap<>();
        page.put("content", content);
        page.put("last", last);
        when(restTemplate.exchange(
            contains("/api/v1/datasets?page=0"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(page));
    }

    private void stubLogicalModels(String datasetId, List<Map<String, Object>> models) {
        when(restTemplate.exchange(
            contains("/api/v1/datasets/" + datasetId + "/logical-models"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(models));
    }

    private void stubElements(String modelId, List<Map<String, Object>> elements) {
        when(restTemplate.exchange(
            contains("/logical-models/" + modelId + "/elements"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(elements));
    }

    private void stubVocabMappings(String elemId, List<Map<String, Object>> mappings) {
        when(restTemplate.exchange(
            contains("/logical-data-elements/" + elemId + "/vocab-mappings"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(mappings));
    }

    private void stubDistributions(String datasetId, List<Map<String, Object>> distributions) {
        when(restTemplate.exchange(
            contains("/api/v1/datasets/" + datasetId + "/distributions"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(distributions));
    }

    private void stubDataProductsEmpty() {
        when(restTemplate.exchange(
            contains("/api/v1/data-products"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(Map.of("content", List.of())));
    }

    private void stubDataProductsWithContent(List<Map<String, Object>> dps) {
        when(restTemplate.exchange(
            contains("/api/v1/data-products"),
            eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(Map.of("content", dps)));
    }

    private Map<String, Object> dataset(String id, String title) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("isDeleted", false);
        m.put("keywords", List.of());
        m.put("themes", List.of());
        return m;
    }

    private Map<String, Object> dataProduct(String id, String title) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("isDeleted", false);
        m.put("keywords", List.of("test"));
        m.put("themes", List.of("Finance"));
        return m;
    }
}
