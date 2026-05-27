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
        try {
            return restClient.post()
                .uri("/api/v1/recommend-semantic-context")
                .body(request)
                .retrieve()
                .body(SemanticRecommendationResponse.class);
        } catch (Exception e) {
            log.warn("AI semantic recommendation service unavailable: {}", e.getMessage());
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

    public List<ElementClassificationResult> classify(List<ElementClassificationInput> elements) {
        try {
            ClassifyResponse response = restClient.post()
                .uri("/api/v1/classify/elements")
                .body(new ClassifyRequest(elements))
                .retrieve()
                .body(ClassifyResponse.class);
            return response != null ? response.results() : List.of();
        } catch (Exception e) {
            log.warn("AI classification service unavailable: {}", e.getMessage());
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
}
