package com.odin.catalog.search.application;

import com.odin.catalog.search.api.v1.dto.ReindexResponse;
import com.odin.catalog.search.infrastructure.opensearch.CatalogSearchDocument;
import com.odin.catalog.search.infrastructure.opensearch.OpenSearchIndexService;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReindexService {

    private static final Logger log = LoggerFactory.getLogger(ReindexService.class);

    private final OpenSearchIndexService indexService;

    @Value("${inventory.service.url:http://inventory-service:8001}")
    private String inventoryServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public ReindexResponse reindex() {
        String tenantId = TenantContextHolder.get();
        HttpHeaders hdrs = buildHeaders(tenantId);
        long t = System.currentTimeMillis();

        int datasetsIndexed = 0;
        int distributionsIndexed = 0;
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String url = inventoryServiceUrl + "/api/v1/datasets?page=" + page + "&size=50";
                ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(hdrs),
                    new ParameterizedTypeReference<Map<String, Object>>() {});

                Map<String, Object> body = resp.getBody();
                if (body == null) break;

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> content = (List<Map<String, Object>>) body.getOrDefault("content", List.of());
                if (content.isEmpty()) break;

                for (Map<String, Object> ds : content) {
                    try {
                        indexService.index(buildEnrichedDatasetDoc(ds, hdrs, tenantId));
                        datasetsIndexed++;
                        distributionsIndexed += indexDistributions((String) ds.get("id"), hdrs, tenantId);
                    } catch (Exception e) {
                        log.warn("action=REINDEX_DATASET_FAILED datasetId={} error={}", ds.get("id"), e.getMessage());
                    }
                }

                Boolean last = (Boolean) body.get("last");
                hasMore = !Boolean.TRUE.equals(last) && content.size() == 50;
                page++;
            } catch (Exception e) {
                log.error("action=REINDEX_PAGE_FAILED page={} error={}", page, e.getMessage());
                break;
            }
        }

        int dataProductsIndexed = reindexDataProducts(hdrs, tenantId);
        log.info("action=REINDEX_COMPLETE datasetsIndexed={} dataProductsIndexed={} distributionsIndexed={} elapsed={}ms",
            datasetsIndexed, dataProductsIndexed, distributionsIndexed, System.currentTimeMillis() - t);
        return new ReindexResponse(datasetsIndexed, dataProductsIndexed, distributionsIndexed);
    }

    @SuppressWarnings("unchecked")
    private CatalogSearchDocument buildEnrichedDatasetDoc(Map<String, Object> ds, HttpHeaders hdrs, String tenantId) {
        String id = (String) ds.get("id");
        List<String> elemNames = new ArrayList<>();
        List<String> logicalTypes = new ArrayList<>();
        List<String> vocabIris = new ArrayList<>();
        List<String> vocabLabels = new ArrayList<>();
        List<String> vocabTypes = new ArrayList<>();
        List<String> fiboConcepts = new ArrayList<>();
        Set<String> semanticTypes = new LinkedHashSet<>();
        boolean hasLogicalModel = false;

        try {
            ResponseEntity<List<Map<String, Object>>> lmResp = restTemplate.exchange(
                inventoryServiceUrl + "/api/v1/datasets/" + id + "/logical-models",
                HttpMethod.GET, new HttpEntity<>(hdrs),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> models = lmResp.getBody();
            if (models != null && !models.isEmpty()) {
                hasLogicalModel = true;
                for (Map<String, Object> lm : models) {
                    String lmId = (String) lm.get("id");
                    enrichFromModel(lmId, hdrs, elemNames, logicalTypes, vocabIris, vocabLabels, vocabTypes, fiboConcepts, semanticTypes);
                }
            }
        } catch (Exception e) {
            log.debug("action=FETCH_LOGICAL_MODELS_FAILED datasetId={} error={}", id, e.getMessage());
        }

        int distCount = 0;
        List<String> distributionFormats = new ArrayList<>();
        try {
            ResponseEntity<List<Map<String, Object>>> distResp = restTemplate.exchange(
                inventoryServiceUrl + "/api/v1/datasets/" + id + "/distributions",
                HttpMethod.GET, new HttpEntity<>(hdrs),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (distResp.getBody() != null) {
                distCount = distResp.getBody().size();
                for (Map<String, Object> dist : distResp.getBody()) {
                    String fmt = (String) dist.get("format");
                    if (fmt != null && !distributionFormats.contains(fmt)) distributionFormats.add(fmt);
                }
            }
        } catch (Exception e) {
            log.debug("action=FETCH_DISTRIBUTIONS_FAILED datasetId={} error={}", id, e.getMessage());
        }

        List<String> keywords = (List<String>) ds.getOrDefault("keywords", List.of());
        List<String> themes   = (List<String>) ds.getOrDefault("themes",   List.of());
        Boolean deleted       = (Boolean)      ds.getOrDefault("isDeleted", false);

        return new CatalogSearchDocument(
            id, tenantId, "DATASET",
            (String) ds.get("title"),
            (String) ds.get("description"),
            keywords != null ? keywords : List.of(),
            themes   != null ? themes   : List.of(),
            (String) ds.get("domainId"), null, null, null, null, null,
            (String) ds.get("license"), null, null,
            (String) ds.get("accrualPeriodicity"),
            (String) ds.get("sourceUri"), null, null,
            Boolean.TRUE.equals(deleted), false, hasLogicalModel, distCount,
            distributionFormats,
            elemNames, List.of(), logicalTypes, vocabIris, vocabLabels, vocabTypes, fiboConcepts,
            new ArrayList<>(semanticTypes),
            List.of(), List.of(), null
        );
    }

    @SuppressWarnings("unchecked")
    private void enrichFromModel(String lmId, HttpHeaders hdrs,
                                  List<String> elemNames, List<String> logicalTypes,
                                  List<String> vocabIris, List<String> vocabLabels,
                                  List<String> vocabTypes, List<String> fiboConcepts,
                                  Set<String> semanticTypes) {
        try {
            ResponseEntity<List<Map<String, Object>>> elemsResp = restTemplate.exchange(
                inventoryServiceUrl + "/api/v1/logical-models/" + lmId + "/elements",
                HttpMethod.GET, new HttpEntity<>(hdrs),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> elems = elemsResp.getBody();
            if (elems == null) return;

            for (Map<String, Object> elem : elems) {
                String name = (String) elem.get("name");
                String lt   = (String) elem.get("logicalType");
                if (name != null) elemNames.add(name);
                if (lt   != null) logicalTypes.add(lt);

                enrichFromVocabMappings((String) elem.get("id"), hdrs, vocabIris, vocabLabels, vocabTypes, fiboConcepts, semanticTypes);
            }
        } catch (Exception e) {
            log.debug("action=FETCH_ELEMENTS_FAILED modelId={} error={}", lmId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichFromVocabMappings(String elemId, HttpHeaders hdrs,
                                          List<String> vocabIris, List<String> vocabLabels,
                                          List<String> vocabTypes, List<String> fiboConcepts,
                                          Set<String> semanticTypes) {
        try {
            ResponseEntity<List<Map<String, Object>>> vmResp = restTemplate.exchange(
                inventoryServiceUrl + "/api/v1/logical-data-elements/" + elemId + "/vocab-mappings",
                HttpMethod.GET, new HttpEntity<>(hdrs),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> mappings = vmResp.getBody();
            if (mappings == null) return;

            for (Map<String, Object> vm : mappings) {
                String iri   = (String) vm.get("conceptIri");
                String label = (String) vm.get("conceptLabel");
                if (iri != null) {
                    vocabIris.add(iri);
                    if (iri.contains("edmcouncil.org/fibo")) {
                        fiboConcepts.add(iri);
                        vocabTypes.add("financial");
                    } else {
                        vocabTypes.add("general");
                    }
                    String matchType = (String) vm.getOrDefault("matchType", "");
                    if ("exactMatch".equals(matchType) || "closeMatch".equals(matchType)) {
                        String fragment = iri.contains("#")
                            ? iri.substring(iri.lastIndexOf('#') + 1)
                            : iri.substring(iri.lastIndexOf('/') + 1);
                        if (!fragment.isBlank()) semanticTypes.add(fragment);
                    }
                }
                if (label != null) vocabLabels.add(label);
            }
        } catch (Exception e) {
            log.debug("action=FETCH_VOCAB_MAPPINGS_FAILED elementId={} error={}", elemId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private int indexDistributions(String datasetId, HttpHeaders hdrs, String tenantId) {
        int count = 0;
        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                inventoryServiceUrl + "/api/v1/datasets/" + datasetId + "/distributions",
                HttpMethod.GET, new HttpEntity<>(hdrs),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> distributions = resp.getBody();
            if (distributions != null) {
                for (Map<String, Object> dist : distributions) {
                    indexService.index(buildDistributionDocument(dist, tenantId, datasetId));
                    count++;
                }
            }
        } catch (Exception e) {
            log.debug("action=INDEX_DISTRIBUTIONS_FAILED datasetId={} error={}", datasetId, e.getMessage());
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int reindexDataProducts(HttpHeaders hdrs, String tenantId) {
        int indexed = 0;
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                inventoryServiceUrl + "/api/v1/data-products?page=0&size=100",
                HttpMethod.GET, new HttpEntity<>(hdrs),
                new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> body = resp.getBody();
            if (body == null) return 0;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) body.getOrDefault("content", List.of());

            for (Map<String, Object> dp : content) {
                String id = (String) dp.get("id");
                List<String> keywords = (List<String>) dp.getOrDefault("keywords", List.of());
                List<String> themes   = (List<String>) dp.getOrDefault("themes",   List.of());
                Boolean deleted       = (Boolean)      dp.getOrDefault("isDeleted", false);

                CatalogSearchDocument doc = new CatalogSearchDocument(
                    id, tenantId, "DATA_PRODUCT",
                    (String) dp.get("title"),
                    (String) dp.get("description"),
                    keywords != null ? keywords : List.of(),
                    themes   != null ? themes   : List.of(),
                    (String) dp.get("domainId"), null, null, null,
                    (String) dp.get("lifecycleStatus"), null, null,
                    null, null, null, null, null, null,
                    Boolean.TRUE.equals(deleted), false, false, 0,
                    List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), null
                );
                indexService.index(doc);
                indexed++;
            }
        } catch (Exception e) {
            log.error("action=REINDEX_DATA_PRODUCTS_FAILED error={}", e.getMessage());
        }
        return indexed;
    }

    @SuppressWarnings("unchecked")
    private CatalogSearchDocument buildDistributionDocument(Map<String, Object> dist, String tenantId, String datasetId) {
        List<String> keywords = (List<String>) dist.getOrDefault("keywords", List.of());
        List<String> themes   = (List<String>) dist.getOrDefault("themes",   List.of());
        return new CatalogSearchDocument(
            (String) dist.get("id"), tenantId, "DISTRIBUTION",
            (String) dist.get("title"),
            (String) dist.get("description"),
            keywords != null ? keywords : List.of(),
            themes   != null ? themes   : List.of(),
            (String) dist.get("domainId"), null, null, null, null, null,
            (String) dist.get("license"),
            (String) dist.get("format"),
            (String) dist.get("mediaType"),
            null, null, null, null, false, false, false, 0,
            null,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), datasetId
        );
    }

    private static HttpHeaders buildHeaders(String tenantId) {
        HttpHeaders hdrs = new HttpHeaders();
        hdrs.set("X-API-Key", "dev-reindex");
        hdrs.set("X-Tenant-Id", tenantId);
        return hdrs;
    }
}
