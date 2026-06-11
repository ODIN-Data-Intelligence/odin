package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationRequest;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationRequest.ElementVocabInput;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationRequest.VocabInfo;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationResponse;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationResponse.ConceptSuggestion;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationResponse.ElementVocabResult;
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
public class VocabConceptRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(VocabConceptRecommendationService.class);
    private static final long TIMEOUT_MINUTES = 5L;
    private static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public VocabConceptRecommendationResponse recommend(VocabConceptRecommendationRequest request) {
        if (request.elements() == null || request.elements().isEmpty()) {
            return new VocabConceptRecommendationResponse(List.of());
        }
        log.info("action=VOCAB_RECOMMEND_START elementCount={}", request.elements().size());
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
            log.warn("action=VOCAB_RECOMMEND_TIMEOUT elementCount={} elapsed={}ms error={}",
                request.elements().size(), System.currentTimeMillis() - t, e.getCause().getMessage());
            return new VocabConceptRecommendationResponse(List.of());
        } catch (Exception e) {
            log.warn("action=VOCAB_RECOMMEND_FAILED elementCount={} elapsed={}ms error={}",
                request.elements().size(), System.currentTimeMillis() - t, e.getMessage());
            return new VocabConceptRecommendationResponse(List.of());
        }
        VocabConceptRecommendationResponse result = parse(raw, request.elements());
        log.info("action=VOCAB_RECOMMEND_COMPLETE elementCount={} resultCount={} elapsed={}ms",
            request.elements().size(), result.results().size(), System.currentTimeMillis() - t);
        return result;
    }

    private String buildPrompt(List<ElementVocabInput> elements) {
        // Collect distinct vocabularies
        String vocabList = elements.stream()
            .flatMap(e -> e.availableVocabularies() == null ? java.util.stream.Stream.empty()
                : e.availableVocabularies().stream())
            .distinct()
            .map(v -> {
                String line = "  - prefix: " + v.prefix() + "  baseIri: " + v.baseIri() + "  name: " + v.name();
                if (v.conceptHints() != null && !v.conceptHints().isBlank())
                    line += "\n    knownConcepts: " + v.conceptHints();
                return line;
            })
            .distinct()
            .collect(Collectors.joining("\n"));

        String elementList = elements.stream().map(el -> {
            StringBuilder sb = new StringBuilder();
            sb.append("- elementId: ").append(el.elementId()).append('\n');
            sb.append("  name: ").append(el.name()).append('\n');
            if (el.label() != null) sb.append("  label: ").append(el.label()).append('\n');
            if (el.logicalType() != null) sb.append("  logicalType: ").append(el.logicalType()).append('\n');
            if (el.description() != null) sb.append("  description: ").append(el.description()).append('\n');
            if (el.existingConceptLabels() != null && !el.existingConceptLabels().isEmpty())
                sb.append("  alreadyMapped: ").append(String.join(", ", el.existingConceptLabels())).append('\n');
            if (el.existingConceptIris() != null && !el.existingConceptIris().isEmpty())
                sb.append("  existingIris: ").append(String.join(", ", el.existingConceptIris())).append('\n');
            return sb.toString();
        }).collect(Collectors.joining("\n"));

        return """
            You are a data catalog specialist. For each data element below, suggest up to 5 relevant SKOS \
            vocabulary concept mappings from the available vocabularies.

            Rules:
            - Use "exactMatch" when the concept precisely describes the element.
            - Use "closeMatch" for near-matches where the concept is very similar but not exact.
            - Use "relatedMatch" when the concept is conceptually relevant but broader or narrower.
            - Do NOT suggest concepts that are already mapped (see "alreadyMapped" and "existingIris").
            - Only suggest IRIs from the available vocabularies listed below.
            - Construct full IRIs by appending the concept name to the baseIri exactly as shown. \
              For path-style bases (ending in /): baseIri + concept/path. \
              For fragment-style bases (ending in #): baseIri + ConceptName. \
              Examples: "https://spec.edmcouncil.org/fibo/ontology/FND/" + "Accounting/CurrencyAmount/MonetaryAmount" \
                        "https://w3id.org/dpv/dpv-pd#" + "EmailAddress" → "https://w3id.org/dpv/dpv-pd#EmailAddress"
            - When a vocabulary lists knownConcepts, prefer those exact IRIs over guessing new ones.
            - Use your knowledge of FIBO, schema.org, DPV, SKOS, and Dublin Core to select appropriate concepts.
            - Omit elements for which you have no confident suggestions rather than guessing.

            Available vocabularies:
            %s

            Output rules (strictly follow these):
            - Respond with a JSON array only. No markdown fences. No explanation outside the JSON.
            - Each array element is a JSON object with fields: elementId, conceptIri, conceptLabel, \
              conceptDefinition, matchType, reasoning.
            - "reasoning" must be a non-empty sentence explaining why this concept fits the element.
            - Include one JSON object per concept suggestion (multiple objects may share the same elementId).

            Example output:
            [{"elementId":"abc-123","conceptIri":"https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount","conceptLabel":"MonetaryAmount","conceptDefinition":"A monetary amount expressed in a specific currency","matchType":"exactMatch","reasoning":"trade_notional represents a monetary value in a given currency"}]

            Data elements to map:
            %s
            """.formatted(vocabList, elementList);
    }

    private VocabConceptRecommendationResponse parse(String raw, List<ElementVocabInput> inputs) {
        log.debug("LLM vocab raw response: {}", raw);
        String cleaned = raw.replaceAll("(?s)```(?:json)?\\s*", "").trim();

        // Group flat list of concept objects by elementId
        java.util.Map<String, List<ConceptSuggestion>> byElement = new java.util.LinkedHashMap<>();
        for (ElementVocabInput el : inputs) byElement.put(el.elementId(), new ArrayList<>());

        int i = 0;
        while (i < cleaned.length()) {
            if (cleaned.charAt(i) == '{') {
                int end = findClosingBrace(cleaned, i);
                if (end > i) {
                    String objJson = cleaned.substring(i, end + 1);
                    try {
                        JsonNode node = objectMapper.readTree(objJson);
                        String elementId  = node.path("elementId").asText(null);
                        String conceptIri = node.path("conceptIri").asText(null);
                        String label      = node.path("conceptLabel").asText(null);
                        String definition = node.path("conceptDefinition").asText(null);
                        String matchType  = node.path("matchType").asText("exactMatch");
                        String reasoning  = node.path("reasoning").asText("");
                        if (elementId != null && conceptIri != null && byElement.containsKey(elementId)) {
                            List<ConceptSuggestion> list = byElement.get(elementId);
                            if (list.size() < 5) {
                                list.add(new ConceptSuggestion(conceptIri, label, definition, matchType, reasoning));
                            }
                        }
                    } catch (Exception ignored) {
                        log.debug("Skipping unparseable vocab object at offset {}", i);
                    }
                    i = end + 1;
                    continue;
                }
            }
            i++;
        }

        List<ElementVocabResult> results = byElement.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .map(e -> new ElementVocabResult(e.getKey(), e.getValue()))
            .toList();

        if (results.isEmpty() && !cleaned.isBlank()) {
            log.warn("Failed to extract any vocab recommendations — raw: {}", raw);
        }
        return new VocabConceptRecommendationResponse(results);
    }

    private int findClosingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false, escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped)               { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true;  continue; }
            if (c == '"')              { inString = !inString; continue; }
            if (inString)              continue;
            if (c == '{')              depth++;
            else if (c == '}')         { if (--depth == 0) return i; }
        }
        return -1;
    }
}
