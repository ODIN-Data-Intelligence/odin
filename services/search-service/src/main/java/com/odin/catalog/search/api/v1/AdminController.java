package com.odin.catalog.search.api.v1;

import com.odin.catalog.search.infrastructure.opensearch.CatalogSearchDocument;
import com.odin.catalog.search.infrastructure.opensearch.OpenSearchIndexService;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Tag(name = "Admin", description = "Administrative operations for the search index — requires elevated API key permissions")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final OpenSearchIndexService indexService;

    @Value("${inventory.service.url:http://inventory-service:8001}")
    private String catalogServiceUrl;

    @Operation(summary = "Full re-index from inventory-service",
        description = "Fetches all datasets and data products from inventory-service page by page and re-indexes them in OpenSearch. "
            + "For each dataset, also fetches logical models, logical elements, and vocabulary mappings to include in the search document. "
            + "Returns counts of indexed documents. Processing is synchronous and may take several minutes for large catalogs.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Re-index complete — returns counts of indexed entities",
            content = @Content(schema = @Schema(example = "{\"datasetsIndexed\": 142, \"dataProductsIndexed\": 31}"))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions — elevated key required", content = @Content)
    })
    @PostMapping("/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> reindex() {
        String tenantId = TenantContextHolder.get();
        RestTemplate rt = new RestTemplate();
        HttpHeaders hdrs = new HttpHeaders();
        hdrs.set("X-API-Key", "dev-reindex");
        hdrs.set("X-Tenant-Id", tenantId);

        int indexed = 0;
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String url = catalogServiceUrl + "/api/v1/datasets?page=" + page + "&size=50";
                ResponseEntity<Map<String, Object>> resp = rt.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(hdrs),
                    new ParameterizedTypeReference<Map<String, Object>>() {});

                Map<String, Object> body = resp.getBody();
                if (body == null) break;

                List<Map<String, Object>> content = (List<Map<String, Object>>) body.getOrDefault("content", List.of());
                if (content.isEmpty()) break;

                for (Map<String, Object> ds : content) {
                    try {
                        CatalogSearchDocument doc = buildEnrichedDatasetDoc(ds, rt, hdrs, tenantId);
                        indexService.index(doc);
                        indexed++;
                    } catch (Exception e) {
                        log.warn("Failed to reindex dataset {}: {}", ds.get("id"), e.getMessage());
                    }
                }

                Boolean last = (Boolean) body.get("last");
                hasMore = !Boolean.TRUE.equals(last) && content.size() == 50;
                page++;
            } catch (Exception e) {
                log.error("Reindex page {} failed: {}", page, e.getMessage());
                break;
            }
        }

        int dpIndexed = reindexDataProducts(rt, hdrs, tenantId);
        return Map.of("datasetsIndexed", indexed, "dataProductsIndexed", dpIndexed);
    }

    @SuppressWarnings("unchecked")
    private CatalogSearchDocument buildEnrichedDatasetDoc(Map<String, Object> ds, RestTemplate rt,
                                                           HttpHeaders hdrs, String tenantId) {
        String id = (String) ds.get("id");
        List<String> elemNames = new ArrayList<>();
        List<String> logicalTypes = new ArrayList<>();
        List<String> vocabIris = new ArrayList<>();
        List<String> vocabLabels = new ArrayList<>();
        List<String> vocabTypes = new ArrayList<>();
        List<String> fiboConcepts = new ArrayList<>();
        boolean hasLogicalModel = false;

        try {
            ResponseEntity<List<Map<String, Object>>> lmResp = rt.exchange(
                catalogServiceUrl + "/api/v1/datasets/" + id + "/logical-models",
                HttpMethod.GET, new HttpEntity<>(hdrs),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            List<Map<String, Object>> models = lmResp.getBody();
            if (models != null && !models.isEmpty()) {
                hasLogicalModel = true;
                for (Map<String, Object> lm : models) {
                    String lmId = (String) lm.get("id");
                    try {
                        ResponseEntity<List<Map<String, Object>>> elemsResp = rt.exchange(
                            catalogServiceUrl + "/api/v1/logical-models/" + lmId + "/elements",
                            HttpMethod.GET, new HttpEntity<>(hdrs),
                            new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                        List<Map<String, Object>> elems = elemsResp.getBody();
                        if (elems != null) {
                            for (Map<String, Object> elem : elems) {
                                String name = (String) elem.get("name");
                                String lt = (String) elem.get("logicalType");
                                if (name != null) elemNames.add(name);
                                if (lt != null) logicalTypes.add(lt);

                                String elemId = (String) elem.get("id");
                                try {
                                    ResponseEntity<List<Map<String, Object>>> vmResp = rt.exchange(
                                        catalogServiceUrl + "/api/v1/logical-data-elements/" + elemId + "/vocab-mappings",
                                        HttpMethod.GET, new HttpEntity<>(hdrs),
                                        new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                                    List<Map<String, Object>> mappings = vmResp.getBody();
                                    if (mappings != null) {
                                        for (Map<String, Object> vm : mappings) {
                                            String iri = (String) vm.get("conceptIri");
                                            String label = (String) vm.get("conceptLabel");
                                            if (iri != null) {
                                                vocabIris.add(iri);
                                                if (iri.contains("edmcouncil.org/fibo")) {
                                                    fiboConcepts.add(iri);
                                                    vocabTypes.add("financial");
                                                } else {
                                                    vocabTypes.add("general");
                                                }
                                            }
                                            if (label != null) vocabLabels.add(label);
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("vocab-mappings for {}: {}", elemId, e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("elements for model {}: {}", lmId, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("logical-models for dataset {}: {}", id, e.getMessage());
        }

        int distCount = 0;
        try {
            ResponseEntity<List<?>> distResp = rt.exchange(
                catalogServiceUrl + "/api/v1/datasets/" + id + "/distributions",
                HttpMethod.GET, new HttpEntity<>(hdrs),
                new ParameterizedTypeReference<List<?>>() {});
            if (distResp.getBody() != null) distCount = distResp.getBody().size();
        } catch (Exception e) {
            log.debug("distributions for dataset {}: {}", id, e.getMessage());
        }

        List<String> keywords = (List<String>) ds.getOrDefault("keywords", List.of());
        List<String> themes = (List<String>) ds.getOrDefault("themes", List.of());
        Boolean deleted = (Boolean) ds.getOrDefault("isDeleted", false);

        return new CatalogSearchDocument(
            id, tenantId, "DATASET",
            (String) ds.get("title"),
            (String) ds.get("description"),
            keywords != null ? keywords : List.of(),
            themes != null ? themes : List.of(),
            (String) ds.get("domainId"), null, null, null, null, null,
            (String) ds.get("license"), null, null,
            (String) ds.get("accrualPeriodicity"),
            (String) ds.get("sourceUri"), null, null,
            Boolean.TRUE.equals(deleted), false, hasLogicalModel, distCount,
            elemNames, List.of(), logicalTypes, vocabIris, vocabLabels, vocabTypes, fiboConcepts,
            List.of(), List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private int reindexDataProducts(RestTemplate rt, HttpHeaders hdrs, String tenantId) {
        int indexed = 0;
        try {
            ResponseEntity<Map<String, Object>> resp = rt.exchange(
                catalogServiceUrl + "/api/v1/data-products?page=0&size=100",
                HttpMethod.GET, new HttpEntity<>(hdrs),
                new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> body = resp.getBody();
            if (body == null) return 0;
            List<Map<String, Object>> content = (List<Map<String, Object>>) body.getOrDefault("content", List.of());

            for (Map<String, Object> dp : content) {
                String id = (String) dp.get("id");
                List<String> keywords = (List<String>) dp.getOrDefault("keywords", List.of());
                List<String> themes = (List<String>) dp.getOrDefault("themes", List.of());
                Boolean deleted = (Boolean) dp.getOrDefault("isDeleted", false);

                CatalogSearchDocument doc = new CatalogSearchDocument(
                    id, tenantId, "DATA_PRODUCT",
                    (String) dp.get("title"),
                    (String) dp.get("description"),
                    keywords != null ? keywords : List.of(),
                    themes != null ? themes : List.of(),
                    (String) dp.get("domainId"), null, null, null,
                    (String) dp.get("lifecycleStatus"), null, null,
                    null, null, null, null, null, null,
                    Boolean.TRUE.equals(deleted), false, false, 0,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of()
                );
                indexService.index(doc);
                indexed++;
            }
        } catch (Exception e) {
            log.error("Failed to reindex data products: {}", e.getMessage());
        }
        return indexed;
    }
}
