package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.PiiRecommendationRequest;
import com.odin.catalog.ai.api.v1.dto.PiiRecommendationRequest.ElementPiiInput;
import com.odin.catalog.ai.api.v1.dto.PiiRecommendationResponse;
import com.odin.catalog.ai.api.v1.dto.PiiRecommendationResponse.ElementPiiResult;
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
public class PiiRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(PiiRecommendationService.class);
    private static final long TIMEOUT_MINUTES = 5L;
    private static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public PiiRecommendationResponse recommend(PiiRecommendationRequest request) {
        if (request.elements() == null || request.elements().isEmpty()) {
            return new PiiRecommendationResponse(List.of());
        }
        log.info("action=PII_RECOMMEND_START elementCount={}", request.elements().size());
        long t = System.currentTimeMillis();
        String prompt = buildPrompt(request.elements());
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
            log.warn("action=PII_RECOMMEND_TIMEOUT elementCount={} elapsed={}ms error={}",
                request.elements().size(), System.currentTimeMillis() - t, e.getCause().getMessage());
            return new PiiRecommendationResponse(List.of());
        } catch (Exception e) {
            log.warn("action=PII_RECOMMEND_FAILED elementCount={} elapsed={}ms error={}",
                request.elements().size(), System.currentTimeMillis() - t, e.getMessage());
            return new PiiRecommendationResponse(List.of());
        }

        log.info("action=PII_RECOMMEND_LLM_COMPLETE elementCount={} elapsed={}ms",
            request.elements().size(), System.currentTimeMillis() - t);

        List<ElementPiiResult> results = parseResponse(raw, request.elements());
        log.info("action=PII_RECOMMEND_COMPLETE elementCount={} parsedCount={} elapsed={}ms",
            request.elements().size(), results.size(), System.currentTimeMillis() - t);
        return new PiiRecommendationResponse(results);
    }

    private String buildPrompt(List<ElementPiiInput> elements) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            You are a data privacy expert. For each data element, output a JSON array entry with \
isPersonalInformation and isDirectIdentifier booleans.

            KEY RULE: isPersonalInformation is true ONLY if this field stores data ABOUT A NATURAL PERSON \
(a human being). Financial transactions, trade IDs, instrument codes, prices, rates, and corporate data \
are NOT personal information.

            Examples:
            - tradeId (Executed Trades dataset) → isPersonalInformation: false, isDirectIdentifier: false \
(trade identifier, not a person)
            - isin (Securities Master) → isPersonalInformation: false, isDirectIdentifier: false \
(instrument code)
            - notionalAmount → isPersonalInformation: false, isDirectIdentifier: false (financial amount)
            - lei (Counterparty Directory of legal entities) → isPersonalInformation: false, \
isDirectIdentifier: false (legal entity identifier)
            - firstName (Customer Profiles) → isPersonalInformation: true, isDirectIdentifier: false \
(person's first name)
            - email (Employee Directory) → isPersonalInformation: true, isDirectIdentifier: true \
(directly identifies a person)
            - nationalId → isPersonalInformation: true, isDirectIdentifier: true (SSN/NIN)
            - traderId (where it maps to an employee/individual) → isPersonalInformation: true, \
isDirectIdentifier: true

            Vocab IRI signals:
            - dpv-pd: IRI → isPersonalInformation: true (DPV Personal Data Category)
            - dpv-pd:Identifying subtype → isDirectIdentifier: true

            Respond ONLY with a JSON array. No markdown, no explanation outside JSON.
            Each entry: { "elementId": "...", "isPersonalInformation": true/false, "isDirectIdentifier": true/false, "reasoning": "one sentence" }

            ELEMENTS:
            """);

        for (ElementPiiInput el : elements) {
            sb.append("---\n");
            sb.append("elementId: ").append(el.elementId()).append("\n");
            sb.append("name: ").append(el.name()).append("\n");
            if (el.label() != null && !el.label().isBlank()) {
                sb.append("label: ").append(el.label()).append("\n");
            }
            if (el.logicalType() != null && !el.logicalType().isBlank()) {
                sb.append("logicalType: ").append(el.logicalType()).append("\n");
            }
            if (el.description() != null && !el.description().isBlank()) {
                sb.append("description: ").append(el.description()).append("\n");
            }
            if (el.vocabConceptIris() != null && !el.vocabConceptIris().isEmpty()) {
                sb.append("vocabConceptIris: ").append(String.join(", ", el.vocabConceptIris())).append("\n");
            }
            if (el.vocabConceptLabels() != null && !el.vocabConceptLabels().isEmpty()) {
                sb.append("vocabConceptLabels: ").append(String.join(", ", el.vocabConceptLabels())).append("\n");
            }
            if (el.datasetTitle() != null && !el.datasetTitle().isBlank()) {
                sb.append("datasetTitle: ").append(el.datasetTitle()).append("\n");
            }
            if (el.datasetKeywords() != null && !el.datasetKeywords().isEmpty()) {
                sb.append("datasetKeywords: ").append(String.join(", ", el.datasetKeywords())).append("\n");
            }
        }

        sb.append("""

            Respond with a JSON array only. Example:
            [
              {"elementId":"abc","isPersonalInformation":true,"isDirectIdentifier":false,"reasoning":"Field name 'email' with customer dataset context indicates personal data but is assessed as indirect only."},
              {"elementId":"def","isPersonalInformation":true,"isDirectIdentifier":true,"reasoning":"dpv-pd:NationalIdentificationNumber IRI confirms direct identifier."}
            ]
            """);

        return sb.toString();
    }

    private List<ElementPiiResult> parseResponse(String raw, List<ElementPiiInput> elements) {
        String json = extractJson(raw);
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) {
                log.warn("action=PII_PARSE_ERROR reason=not_array raw={}", abbreviate(raw));
                return fallback(elements);
            }
            List<ElementPiiResult> results = new ArrayList<>();
            for (JsonNode node : arr) {
                String elementId = node.path("elementId").asText(null);
                if (elementId == null || elementId.isBlank()) continue;
                boolean isPii = node.path("isPersonalInformation").asBoolean(false);
                boolean isDirect = node.path("isDirectIdentifier").asBoolean(false);
                String reasoning = node.path("reasoning").asText("");
                // direct identifier implies personal information
                if (isDirect) isPii = true;
                results.add(new ElementPiiResult(elementId, isPii, isDirect, reasoning));
            }
            return results;
        } catch (Exception e) {
            log.warn("action=PII_PARSE_FAILED error={} raw={}", e.getMessage(), abbreviate(raw));
            return fallback(elements);
        }
    }

    private List<ElementPiiResult> fallback(List<ElementPiiInput> elements) {
        return elements.stream()
            .map(e -> new ElementPiiResult(e.elementId(), false, false,
                "LLM response could not be parsed — defaulted to false."))
            .collect(Collectors.toList());
    }

    private static String extractJson(String raw) {
        if (raw == null) return "[]";
        String trimmed = raw.strip();
        // Strip markdown code fences
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).strip();
            }
        }
        // Find array bounds
        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }
        return trimmed;
    }

    private static String abbreviate(String s) {
        if (s == null) return "null";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
