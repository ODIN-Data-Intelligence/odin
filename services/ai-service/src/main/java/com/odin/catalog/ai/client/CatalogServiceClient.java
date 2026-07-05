package com.odin.catalog.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Consumer;

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

    public List<Distribution> getDistributions(String datasetId) {
        try {
            return restClient.get()
                .uri("/api/v1/datasets/{id}/distributions", datasetId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not fetch distributions for dataset {}: {}", datasetId, e.getMessage());
            return List.of();
        }
    }

    public List<PhysicalColumn> getDistributionPhysicalSchema(String distributionId) {
        try {
            return restClient.get()
                .uri("/api/v1/distributions/{id}/physical-schema", distributionId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not fetch physical schema for distribution {}: {}", distributionId, e.getMessage());
            return List.of();
        }
    }

    public List<PhysicalColumn> getDatasetPhysicalSchema(String datasetId) {
        try {
            return restClient.get()
                .uri("/api/v1/datasets/{id}/physical-schema", datasetId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not fetch physical schema for dataset {}: {}", datasetId, e.getMessage());
            return List.of();
        }
    }

    // ── Agentic review: auth-forwarding context fetch + persist ────────────────
    //
    // The agentic loop runs on a virtual thread spawned off the request thread, so it cannot
    // rely on the ThreadLocal SecurityContext. Instead the caller's Authorization header is
    // captured on the request thread and forwarded here, so inventory-service resolves the
    // real tenant/user (JWT wins over the default dev X-API-Key). For the write (persist) this
    // also keeps the audit trail attributed to the acting user.

    public DatasetSummary getDataset(String datasetId, String authHeader) {
        try {
            return restClient.get()
                .uri("/api/v1/datasets/{id}", datasetId)
                .headers(forward(authHeader))
                .retrieve()
                .body(DatasetSummary.class);
        } catch (Exception e) {
            log.warn("Could not fetch dataset {}: {}", datasetId, e.getMessage());
            return null;
        }
    }

    public List<LogicalElement> getLogicalElements(String modelId, String authHeader) {
        try {
            return restClient.get()
                .uri("/api/v1/logical-models/{id}/elements", modelId)
                .headers(forward(authHeader))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not fetch elements for model {}: {}", modelId, e.getMessage());
            return List.of();
        }
    }

    public List<Distribution> getDistributions(String datasetId, String authHeader) {
        try {
            return restClient.get()
                .uri("/api/v1/datasets/{id}/distributions", datasetId)
                .headers(forward(authHeader))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not fetch distributions for dataset {}: {}", datasetId, e.getMessage());
            return List.of();
        }
    }

    public List<Vocabulary> getVocabularies(String authHeader) {
        try {
            return restClient.get()
                .uri("/api/v1/vocabularies")
                .headers(forward(authHeader))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not fetch vocabularies: {}", e.getMessage());
            return List.of();
        }
    }

    /** Persists the converged agentic proposal onto the model's elements (pending recommendation columns). */
    public void applyAgenticRecommendations(String modelId, AgenticRecommendationsPayload payload, String authHeader) {
        restClient.post()
            .uri("/api/v1/logical-models/{id}/agentic-recommendations", modelId)
            .headers(forward(authHeader))
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }

    private static Consumer<HttpHeaders> forward(String authHeader) {
        return headers -> {
            if (authHeader != null && !authHeader.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            }
        };
    }

    // ── Agentic persist payload ────────────────────────────────────────────────

    public record AgenticRecommendationsPayload(List<ElementRecommendation> elements) {}

    public record ElementRecommendation(
        String elementId,
        String description,
        String descriptionReasoning,
        String classification,
        String classificationReasoning,
        List<VocabConcept> vocabConcepts,
        Boolean isPersonalInformation,
        Boolean isDirectIdentifier,
        String piiReasoning
    ) {}

    public record VocabConcept(
        String conceptIri,
        String conceptLabel,
        String conceptDefinition,
        String matchType,
        String reasoning
    ) {}

    // ── Projection records ────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DatasetSummary(
        String id, String title, String description,
        List<String> keywords, List<String> themes,
        String tenantId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LogicalModel(String id, String name, String status, String version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LogicalElement(
        String id, String name, String label, String description, String logicalType,
        boolean isIdentifier,
        String classification,
        boolean isPersonalInformation,
        boolean isDirectIdentifier,
        List<VocabMapping> vocabMappings
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vocabulary(
        String prefix, String baseIri, String name, String conceptHints
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VocabMapping(
        String conceptIri, String conceptLabel, String conceptDefinition, String matchType
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Distribution(
        String id, String title, String description,
        String format, String mediaType,
        String accessUrl, String downloadUrl,
        String databaseName, String schemaName, String tableName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhysicalColumn(
        String id, String name, String datatype,
        Boolean required, Integer ordinal,
        String logicalDataElementId
    ) {}
}
