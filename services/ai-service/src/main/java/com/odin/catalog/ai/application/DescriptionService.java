package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.ClassifyElementsRequest;
import com.odin.catalog.ai.api.v1.dto.DescribeElementsResponse;
import com.odin.catalog.ai.api.v1.dto.DescribeElementsResponse.ElementResult;
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
public class DescriptionService {

    private static final Logger log = LoggerFactory.getLogger(DescriptionService.class);
    private static final long DESCRIPTION_TIMEOUT_MINUTES = 5L;
    private static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public DescribeElementsResponse describe(ClassifyElementsRequest request) {
        if (request.elements() == null || request.elements().isEmpty()) {
            return new DescribeElementsResponse(List.of());
        }
        log.info("action=DESCRIPTION_RECOMMEND_START elementCount={}", request.elements().size());
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
                .orTimeout(DESCRIPTION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .join();
        } catch (CompletionException e) {
            log.warn("action=DESCRIPTION_TIMEOUT elementCount={} elapsed={}ms error={}",
                request.elements().size(), System.currentTimeMillis() - t, e.getCause().getMessage());
            return new DescribeElementsResponse(List.of());
        } catch (Exception e) {
            log.warn("action=DESCRIPTION_FAILED elementCount={} elapsed={}ms error={}",
                request.elements().size(), System.currentTimeMillis() - t, e.getMessage());
            return new DescribeElementsResponse(List.of());
        }
        DescribeElementsResponse result = parse(raw);
        log.info("action=DESCRIPTION_RECOMMEND_COMPLETE elementCount={} resultCount={} elapsed={}ms",
            request.elements().size(), result.results().size(), System.currentTimeMillis() - t);
        return result;
    }

    private String buildPrompt(List<ClassifyElementsRequest.ElementInput> elements) {
        String elementList = elements.stream().map(el -> {
            StringBuilder sb = new StringBuilder();
            sb.append("- elementId: ").append(el.elementId()).append('\n');
            sb.append("  name: ").append(el.name()).append('\n');
            if (el.label() != null) sb.append("  label: ").append(el.label()).append('\n');
            if (el.logicalType() != null) sb.append("  logicalType: ").append(el.logicalType()).append('\n');
            if (el.vocabConceptLabels() != null && !el.vocabConceptLabels().isEmpty())
                sb.append("  vocabConcepts: ").append(String.join(", ", el.vocabConceptLabels())).append('\n');
            if (el.vocabConceptIris() != null && !el.vocabConceptIris().isEmpty())
                sb.append("  vocabIris: ").append(String.join(", ", el.vocabConceptIris())).append('\n');
            return sb.toString();
        }).collect(Collectors.joining("\n"));

        return """
            You are a data governance expert. For each data element below, write a clear, concise business
            description (one to two sentences) that explains what the field represents to a non-technical
            business user. Ground your description in the provided vocabulary concept mappings — use the
            FIBO or schema.org concept labels to explain the business meaning precisely.

            Rules:
            - Write for a business audience, not a technical one. Avoid jargon.
            - Ground the description in the vocabulary concepts provided (FIBO, schema.org, SKOS).
            - Do NOT repeat the element name verbatim — describe what it *means*.
            - Keep descriptions to 1–2 sentences maximum.
            - If no vocabulary concepts are provided, use the element name, label, and logical type to infer meaning.
            - When uncertain, omit the element from the response entirely rather than guessing.

            Output rules (strictly follow):
            - Respond with a JSON array only. No markdown fences. No explanation outside the JSON.
            - Each array element must be an object with "elementId", "description", and "reasoning" fields.
            - "description" is the suggested business description (1–2 sentences).
            - "reasoning" is one sentence explaining which vocabulary concepts informed the description.

            Example output:
            [{"elementId":"abc-123","description":"The monetary value of the trade at the time of execution, denominated in the settlement currency.","reasoning":"Derived from the FIBO MonetaryAmount concept which defines a quantity of money expressed in a specific currency."}]

            Elements to describe:
            %s
            """.formatted(elementList);
    }

    private DescribeElementsResponse parse(String raw) {
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
                        String description = node.path("description").asText(null);
                        String reasoning = node.path("reasoning").asText("");
                        if (elementId != null && description != null && !description.isBlank()) {
                            results.add(new ElementResult(elementId, description, reasoning));
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
            log.warn("Failed to extract any description results — raw: {}", raw);
        }
        return new DescribeElementsResponse(results);
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
