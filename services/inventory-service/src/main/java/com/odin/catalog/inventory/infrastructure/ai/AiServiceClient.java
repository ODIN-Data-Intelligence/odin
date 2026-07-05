package com.odin.catalog.inventory.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    // LLM inference can take several minutes; match the Ollama timeout in AiConfig
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(6);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;

    public AiServiceClient(
            RestClient.Builder builder,
            @Value("${ai.service.url:http://ai-service:8005}") String baseUrl,
            @Value("${ai.service.api-key:dev-local}") String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(READ_TIMEOUT);
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        this.restClient = builder
            .baseUrl(baseUrl)
            .defaultHeader("X-API-Key", apiKey)
            .requestFactory(factory)
            .build();
    }

    public SemanticRecommendationResponse recommendSemanticContext(SemanticRecommendationRequest request) {
        log.info("action=AI_REQUEST_START operation=recommendSemanticContext datasetId={}", request.datasetId());
        long t = System.currentTimeMillis();
        try {
            SemanticRecommendationResponse response = restClient.post()
                .uri("/api/v1/recommend-semantic-context")
                .body(request)
                .retrieve()
                .body(SemanticRecommendationResponse.class);
            log.info("action=AI_REQUEST_COMPLETE operation=recommendSemanticContext datasetId={} typeCount={} elapsed={}ms",
                request.datasetId(), response != null && response.types() != null ? response.types().size() : 0, System.currentTimeMillis() - t);
            return response;
        } catch (Exception e) {
            log.warn("action=AI_REQUEST_FAILED operation=recommendSemanticContext datasetId={} elapsed={}ms error={}",
                request.datasetId(), System.currentTimeMillis() - t, e.getMessage());
            throw new AiServiceUnavailableException("Semantic recommendation service is unavailable", e);
        }
    }

    public record SemanticRecommendationRequest(
        String datasetId,
        String title,
        String description,
        List<String> keywords,
        List<String> themes,
        List<String> elementNames,
        List<String> logicalTypes,
        List<String> currentVocabLabels,
        List<String> currentVocabIris
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SemanticRecommendationResponse(
        List<RecommendedType> types,
        String rationale
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record RecommendedType(
            String type,
            String rationale,
            String vocabularyHint
        ) {}
    }

    public List<ElementDescriptionResult> describeElements(List<ElementClassificationInput> elements) {
        log.info("action=AI_REQUEST_START operation=describeElements elementCount={}", elements.size());
        long t = System.currentTimeMillis();
        try {
            DescribeResponse response = restClient.post()
                .uri("/api/v1/describe/elements")
                .body(new ClassifyRequest(elements))
                .retrieve()
                .body(DescribeResponse.class);
            List<ElementDescriptionResult> results = response != null ? response.results() : List.of();
            log.info("action=AI_REQUEST_COMPLETE operation=describeElements elementCount={} resultCount={} elapsed={}ms",
                elements.size(), results.size(), System.currentTimeMillis() - t);
            return results;
        } catch (Exception e) {
            log.warn("action=AI_REQUEST_FAILED operation=describeElements elementCount={} elapsed={}ms error={}",
                elements.size(), System.currentTimeMillis() - t, e.getMessage());
            throw new AiServiceUnavailableException("Description recommendation service is unavailable", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ElementDescriptionResult(
        String elementId,
        String description,
        String reasoning
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DescribeResponse(List<ElementDescriptionResult> results) {}

    public List<VocabConceptRecommendation> recommendVocabConcepts(List<ElementVocabInput> elements) {
        log.info("action=AI_REQUEST_START operation=recommendVocabConcepts elementCount={}", elements.size());
        long t = System.currentTimeMillis();
        try {
            VocabConceptRecommendationResponse response = restClient.post()
                .uri("/api/v1/recommend-vocab-concepts")
                .body(new VocabConceptRecommendationRequest(elements))
                .retrieve()
                .body(VocabConceptRecommendationResponse.class);
            List<VocabConceptRecommendation> results = response != null ? response.results() : List.of();
            log.info("action=AI_REQUEST_COMPLETE operation=recommendVocabConcepts elementCount={} resultCount={} elapsed={}ms",
                elements.size(), results.size(), System.currentTimeMillis() - t);
            return results;
        } catch (Exception e) {
            log.warn("action=AI_REQUEST_FAILED operation=recommendVocabConcepts elementCount={} elapsed={}ms error={}",
                elements.size(), System.currentTimeMillis() - t, e.getMessage());
            throw new AiServiceUnavailableException("Vocabulary concept recommendation service is unavailable", e);
        }
    }

    public record VocabInfo(String prefix, String baseIri, String name, String conceptHints) {}

    public record ElementVocabInput(
        String elementId,
        String name,
        String label,
        String logicalType,
        String description,
        List<String> existingConceptIris,
        List<String> existingConceptLabels,
        List<VocabInfo> availableVocabularies
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecommendedConcept(
        String conceptIri,
        String conceptLabel,
        String conceptDefinition,
        String matchType,
        String reasoning
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VocabConceptRecommendation(
        String elementId,
        List<RecommendedConcept> concepts
    ) {}

    private record VocabConceptRecommendationRequest(List<ElementVocabInput> elements) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VocabConceptRecommendationResponse(List<VocabConceptRecommendation> results) {}

    public List<ElementClassificationResult> classify(List<ElementClassificationInput> elements) {
        log.info("action=AI_REQUEST_START operation=classify elementCount={}", elements.size());
        long t = System.currentTimeMillis();
        try {
            ClassifyResponse response = restClient.post()
                .uri("/api/v1/classify/elements")
                .body(new ClassifyRequest(elements))
                .retrieve()
                .body(ClassifyResponse.class);
            List<ElementClassificationResult> results = response != null ? response.results() : List.of();
            log.info("action=AI_REQUEST_COMPLETE operation=classify elementCount={} resultCount={} elapsed={}ms",
                elements.size(), results.size(), System.currentTimeMillis() - t);
            return results;
        } catch (Exception e) {
            log.warn("action=AI_REQUEST_FAILED operation=classify elementCount={} elapsed={}ms error={}",
                elements.size(), System.currentTimeMillis() - t, e.getMessage());
            throw new AiServiceUnavailableException("Classification service is unavailable", e);
        }
    }

    public record ElementClassificationInput(
        String elementId,
        String name,
        String label,
        String logicalType,
        String description,
        List<String> vocabConceptIris,
        List<String> vocabConceptLabels
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ElementClassificationResult(
        String elementId,
        String classification,
        String reasoning
    ) {}

    private record ClassifyRequest(List<ElementClassificationInput> elements) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClassifyResponse(List<ElementClassificationResult> results) {}

    public List<ElementPiiResult> recommendPii(List<ElementPiiInput> elements) {
        log.info("action=AI_REQUEST_START operation=recommendPii elementCount={}", elements.size());
        long t = System.currentTimeMillis();
        try {
            PiiRecommendationResponse response = restClient.post()
                .uri("/api/v1/recommend-pii")
                .body(new PiiRecommendationRequest(elements))
                .retrieve()
                .body(PiiRecommendationResponse.class);
            List<ElementPiiResult> results = response != null ? response.results() : List.of();
            log.info("action=AI_REQUEST_COMPLETE operation=recommendPii elementCount={} resultCount={} elapsed={}ms",
                elements.size(), results.size(), System.currentTimeMillis() - t);
            return results;
        } catch (Exception e) {
            log.warn("action=AI_REQUEST_FAILED operation=recommendPii elementCount={} elapsed={}ms error={}",
                elements.size(), System.currentTimeMillis() - t, e.getMessage());
            throw new AiServiceUnavailableException("PII recommendation service is unavailable", e);
        }
    }

    public record ElementPiiInput(
        String elementId,
        String name,
        String label,
        String logicalType,
        String description,
        List<String> vocabConceptIris,
        List<String> vocabConceptLabels,
        String datasetTitle,
        List<String> datasetKeywords
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ElementPiiResult(
        String elementId,
        boolean isPersonalInformation,
        boolean isDirectIdentifier,
        String reasoning
    ) {}

    private record PiiRecommendationRequest(List<ElementPiiInput> elements) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PiiRecommendationResponse(List<ElementPiiResult> results) {}
}
