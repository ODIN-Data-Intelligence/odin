package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Robust extraction of top-level JSON objects from raw LLM output. Local models frequently
 * wrap JSON in markdown fences, add commentary, quote objects as strings, or omit the outer
 * array brackets entirely — so we brace-scan the text and parse each balanced {...} block
 * independently rather than trusting the response to be a single well-formed document.
 *
 * <p>Mirrors the parser previously embedded in {@code ClassificationService}/{@code
 * VocabConceptRecommendationService} and is shared by the agentic proposer/reviewer loop.
 */
public final class JsonExtractionSupport {

    private static final Logger log = LoggerFactory.getLogger(JsonExtractionSupport.class);

    private JsonExtractionSupport() {}

    /** Returns every top-level JSON object parsed from {@code raw}, in document order. */
    public static List<JsonNode> extractObjects(String raw, ObjectMapper mapper) {
        List<JsonNode> nodes = new ArrayList<>();
        if (raw == null || raw.isBlank()) return nodes;
        String cleaned = raw.replaceAll("(?s)```(?:json)?\\s*", "").trim();
        int i = 0;
        while (i < cleaned.length()) {
            if (cleaned.charAt(i) == '{') {
                int end = findClosingBrace(cleaned, i);
                if (end > i) {
                    String objJson = cleaned.substring(i, end + 1);
                    try {
                        nodes.add(mapper.readTree(objJson));
                    } catch (Exception ignored) {
                        log.debug("Skipping unparseable JSON object at offset {}", i);
                    }
                    i = end + 1;
                    continue;
                }
            }
            i++;
        }
        return nodes;
    }

    /** Index of the '}' matching the '{' at {@code start}, honouring strings/escapes; -1 if unbalanced. */
    public static int findClosingBrace(String s, int start) {
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
