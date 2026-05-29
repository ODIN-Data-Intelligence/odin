package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.SemanticRecommendationRequest;
import com.odin.catalog.ai.api.v1.dto.SemanticRecommendationResponse;
import com.odin.catalog.ai.api.v1.dto.SemanticRecommendationResponse.RecommendedType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SemanticRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(SemanticRecommendationService.class);
    private static final long TIMEOUT_MINUTES = 5L;
    private static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SemanticRecommendationResponse recommend(SemanticRecommendationRequest request) {
        log.info("action=SEMANTIC_RECOMMEND_START datasetId={} elementCount={}", request.datasetId(),
            request.elementNames() != null ? request.elementNames().size() : 0);
        long t = System.currentTimeMillis();
        String prompt = buildPrompt(request);
        String raw;
        try {
            raw = CompletableFuture
                .supplyAsync(() -> chatClient.prompt()
                    .system("/no_think")
                    .user(prompt)
                    .call()
                    .content(), VIRTUAL_EXECUTOR)
                .orTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .join();
        } catch (CompletionException e) {
            log.warn("action=SEMANTIC_RECOMMEND_TIMEOUT datasetId={} elapsed={}ms error={}",
                request.datasetId(), System.currentTimeMillis() - t, e.getCause().getMessage());
            return new SemanticRecommendationResponse(List.of(), "Service unavailable");
        } catch (Exception e) {
            log.warn("action=SEMANTIC_RECOMMEND_FAILED datasetId={} elapsed={}ms error={}",
                request.datasetId(), System.currentTimeMillis() - t, e.getMessage());
            return new SemanticRecommendationResponse(List.of(), "Service unavailable");
        }
        SemanticRecommendationResponse result = parse(raw);
        log.info("action=SEMANTIC_RECOMMEND_COMPLETE datasetId={} typeCount={} elapsed={}ms",
            request.datasetId(), result.types().size(), System.currentTimeMillis() - t);
        return result;
    }

    private String buildPrompt(SemanticRecommendationRequest req) {
        String elementSummary = req.elementNames() != null && !req.elementNames().isEmpty()
            ? String.join(", ", req.elementNames())
            : "none";
        String typeSummary = req.logicalTypes() != null && !req.logicalTypes().isEmpty()
            ? req.logicalTypes().stream().distinct().collect(Collectors.joining(", "))
            : "none";
        String keywordSummary = req.keywords() != null && !req.keywords().isEmpty()
            ? String.join(", ", req.keywords())
            : "none";
        String themeSummary = req.themes() != null && !req.themes().isEmpty()
            ? String.join(", ", req.themes())
            : "none";
        String currentConcepts = req.currentVocabLabels() != null && !req.currentVocabLabels().isEmpty()
            ? String.join(", ", req.currentVocabLabels())
            : "none";

        return """
            You are a data stewardship AI. Given a dataset's metadata and current vocabulary mappings, \
            identify the key business domain types this dataset represents, and recommend additional \
            vocabulary concepts that would improve its semantic coverage.

            Dataset metadata:
            - Title: %s
            - Description: %s
            - Keywords: %s
            - Themes: %s
            - Data element names: %s
            - Logical types used: %s
            - Already mapped vocabulary concepts: %s

            Your task:
            1. Determine which high-level business domain types this dataset represents \
            (e.g. "Customer", "Trade", "Account", "Payment", "RiskMetric").
            2. For each type, recommend specific FIBO or schema.org vocabulary concepts \
            that should be mapped to data elements to improve semantic richness.
            3. Do NOT repeat types already covered by the mapped vocabulary concepts.
            4. Return at most 5 recommendations, ordered by relevance.

            Output rules (strictly follow):
            - Respond with a JSON array only. No markdown fences. No explanation outside the JSON.
            - Each item: {"type":"...","rationale":"...","vocabularyHint":"..."}
            - "type": concise business domain concept name (1-3 words, PascalCase)
            - "rationale": one sentence explaining why this dataset represents this type
            - "vocabularyHint": a relevant FIBO IRI prefix or schema.org type (e.g. \
            "https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/ClientsAndAccounts/Customer" \
            or "https://schema.org/Person"), or empty string if none applicable

            Example output:
            [{"type":"Trade","rationale":"Element names like tradeId and tradePrice indicate this dataset records financial trade executions.","vocabularyHint":"https://spec.edmcouncil.org/fibo/ontology/SEC/Securities/SecuritiesListings/ListedSecurity"}]
            """.formatted(
                nullSafe(req.title()),
                nullSafe(req.description()),
                keywordSummary,
                themeSummary,
                elementSummary,
                typeSummary,
                currentConcepts);
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private SemanticRecommendationResponse parse(String raw) {
        log.debug("Semantic recommendation raw response: {}", raw);
        String cleaned = raw.replaceAll("(?s)```(?:json)?\\s*", "").trim();
        List<RecommendedType> types = new ArrayList<>();
        int i = 0;
        while (i < cleaned.length()) {
            if (cleaned.charAt(i) == '{') {
                int end = findClosingBrace(cleaned, i);
                if (end > i) {
                    String objJson = cleaned.substring(i, end + 1);
                    try {
                        JsonNode node = objectMapper.readTree(objJson);
                        String type = node.path("type").asText(null);
                        String rationale = node.path("rationale").asText("");
                        String hint = node.path("vocabularyHint").asText("");
                        if (type != null && !type.isBlank()) {
                            types.add(new RecommendedType(type, rationale, hint.isBlank() ? null : hint));
                        }
                    } catch (Exception ignored) {
                        log.debug("Skipping unparseable object at offset {}", i);
                    }
                    i = end + 1;
                    continue;
                }
            }
            i++;
        }
        String summary = types.isEmpty()
            ? "No additional recommendations found."
            : "Found " + types.size() + " semantic type recommendation" + (types.size() > 1 ? "s" : "") + ".";
        return new SemanticRecommendationResponse(types, summary);
    }

    private int findClosingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false, escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped)               { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"')              { inString = !inString; continue; }
            if (inString)              continue;
            if (c == '{')              depth++;
            else if (c == '}')         { if (--depth == 0) return i; }
        }
        return -1;
    }
}
