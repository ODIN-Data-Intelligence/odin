package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.ClassifyElementsRequest;
import com.odin.catalog.ai.api.v1.dto.ClassifyElementsResponse;
import com.odin.catalog.ai.api.v1.dto.ClassifyElementsResponse.ElementResult;
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
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);
    private static final long CLASSIFICATION_TIMEOUT_MINUTES = 5L;
    private static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ClassifyElementsResponse classify(ClassifyElementsRequest request) {
        if (request.elements() == null || request.elements().isEmpty()) {
            return new ClassifyElementsResponse(List.of());
        }
        String prompt = buildPrompt(request.elements());
        String raw;
        try {
            raw = CompletableFuture
                .supplyAsync(() -> chatClient.prompt().user(prompt).call().content(), VIRTUAL_EXECUTOR)
                .orTimeout(CLASSIFICATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .join();
        } catch (CompletionException e) {
            log.warn("LLM classification timed out or failed: {}", e.getCause().getMessage());
            return new ClassifyElementsResponse(List.of());
        } catch (Exception e) {
            log.warn("LLM classification call failed: {}", e.getMessage());
            return new ClassifyElementsResponse(List.of());
        }
        return parse(raw);
    }

    private String buildPrompt(List<ClassifyElementsRequest.ElementInput> elements) {
        String elementList = elements.stream().map(el -> {
            StringBuilder sb = new StringBuilder();
            sb.append("- elementId: ").append(el.elementId()).append('\n');
            sb.append("  name: ").append(el.name()).append('\n');
            if (el.label() != null) sb.append("  label: ").append(el.label()).append('\n');
            if (el.logicalType() != null) sb.append("  logicalType: ").append(el.logicalType()).append('\n');
            if (el.description() != null) sb.append("  description: ").append(el.description()).append('\n');
            if (el.vocabConceptLabels() != null && !el.vocabConceptLabels().isEmpty())
                sb.append("  vocabConcepts: ").append(String.join(", ", el.vocabConceptLabels())).append('\n');
            if (el.vocabConceptIris() != null && !el.vocabConceptIris().isEmpty())
                sb.append("  vocabIris: ").append(String.join(", ", el.vocabConceptIris())).append('\n');
            return sb.toString();
        }).collect(Collectors.joining("\n"));

        return """
            You are a data governance expert. Classify each data element below into exactly one of these four levels:
              PUBLIC           – Freely shareable public information (public market prices, public company names)
              INTERNAL         – Internal-only, non-sensitive operational data (reference codes, aggregate stats)
              CONFIDENTIAL     – Personal or financial data that must be protected (name, email, trade positions)
              HIGH_CONFIDENTIAL – Highly sensitive: identifiers, credentials, health, payment card data, SSNs

	    Apply Schema.org and FIBO ontologies to inform your classifications, but use only the provided
            metadata (name, description, logical type, vocab concepts) for your reasoning. Do not make assumptions
            beyond the given information.

            Classification signals:
            - FIBO financial concepts (fibo-fnd, fibo-fbc, fibo-sec) → CONFIDENTIAL unless aggregate/public market data
            - Schema.org Person, email, telephone, identifier → CONFIDENTIAL or HIGH_CONFIDENTIAL
            - Credential, password, biometric, SSN, card number → HIGH_CONFIDENTIAL
            - Public market prices, public company names → PUBLIC
            - Aggregate statistics, operational metrics, reference codes → INTERNAL
            - When uncertain, omit the element from the response entirely

            Output rules (strictly follow these):
            - Respond with a JSON array only. No markdown fences. No explanation outside the JSON.
            - Each array element must be a JSON object (not a string).
            - The "classification" field must contain exactly one word: PUBLIC, INTERNAL, CONFIDENTIAL, or HIGH_CONFIDENTIAL.

            Example output:
            [{"elementId":"abc-123","classification":"CONFIDENTIAL","reasoning":"Contains personal financial position data"}]

            Elements to classify:
            %s
            """.formatted(elementList);
    }

    /**
     * Extracts all top-level JSON objects ({...}) from the raw LLM output and maps each
     * to an ElementResult. This approach is robust to the LLM wrapping objects in quotes,
     * adding extra commentary, or omitting the outer array brackets entirely.
     */
    private ClassifyElementsResponse parse(String raw) {
        log.debug("LLM raw response: {}", raw);
        String cleaned = raw.replaceAll("(?s)```(?:json)?\\s*", "").trim();
        List<ElementResult> results = new ArrayList<>();
        int i = 0;
        while (i < cleaned.length()) {
            if (cleaned.charAt(i) == '{') {
                int end = findClosingBrace(cleaned, i);
                if (end > i) {
                    String objJson = cleaned.substring(i, end + 1);
                    try {
                        JsonNode node = objectMapper.readTree(objJson);
                        String elementId = node.path("elementId").asText(null);
                        String classification = node.path("classification").asText(null);
                        String reasoning = node.path("reasoning").asText("");
                        if (elementId != null && isValidLevel(classification)) {
                            results.add(new ElementResult(elementId, classification, reasoning));
                        }
                    } catch (Exception ignored) {
                        log.debug("Skipping unparseable object at offset {}: {}", i, objJson);
                    }
                    i = end + 1;
                    continue;
                }
            }
            i++;
        }
        if (results.isEmpty() && !cleaned.isBlank()) {
            log.warn("Failed to extract any classification results — raw: {}", raw);
        }
        return new ClassifyElementsResponse(results);
    }

    // Returns the index of the matching '}' for the '{' at `start`, or -1 if unbalanced.
    private int findClosingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false, escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped)          { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"')         { inString = !inString; continue; }
            if (inString)         continue;
            if (c == '{')         depth++;
            else if (c == '}')    { if (--depth == 0) return i; }
        }
        return -1;
    }

    private boolean isValidLevel(String level) {
        return "PUBLIC".equals(level) || "INTERNAL".equals(level)
            || "CONFIDENTIAL".equals(level) || "HIGH_CONFIDENTIAL".equals(level);
    }
}
