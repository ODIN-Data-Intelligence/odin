package com.odin.catalog.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class CatalogServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogServiceClient.class);

    private final RestClient restClient;

    public CatalogServiceClient(
            RestClient.Builder builder,
            @Value("${catalog.service.url}") String baseUrl,
            @Value("${catalog.service.api-key}") String apiKey) {
        this.restClient = builder
            .baseUrl(baseUrl)
            .defaultHeader("X-API-Key", apiKey)
            .build();
    }

    public DatasetSummary getDataset(String datasetId) {
        try {
            return restClient.get()
                .uri("/api/v1/datasets/{id}", datasetId)
                .retrieve()
                .body(DatasetSummary.class);
        } catch (Exception e) {
            log.warn("Could not fetch dataset {}: {}", datasetId, e.getMessage());
            return null;
        }
    }

    public List<LogicalModel> getLogicalModels(String datasetId) {
        try {
            return restClient.get()
                .uri("/api/v1/datasets/{id}/logical-models", datasetId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not fetch logical models for dataset {}: {}", datasetId, e.getMessage());
            return List.of();
        }
    }

    public List<LogicalElement> getLogicalElements(String modelId) {
        try {
            return restClient.get()
                .uri("/api/v1/logical-models/{id}/elements", modelId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not fetch elements for model {}: {}", modelId, e.getMessage());
            return List.of();
        }
    }

    // ── Projection records ────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DatasetSummary(
        String id, String title, String description,
        List<String> keywords, List<String> themes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LogicalModel(String id, String name, String status, String version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LogicalElement(
        String id, String name, String label, String description, String logicalType,
        List<VocabMapping> vocabMappings
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VocabMapping(
        String conceptIri, String conceptLabel, String conceptDefinition, String matchType
    ) {}
}
