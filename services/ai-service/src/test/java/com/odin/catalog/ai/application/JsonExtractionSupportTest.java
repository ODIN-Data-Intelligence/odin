package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonExtractionSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractObjects_plainArray_returnsEachObject() {
        List<JsonNode> nodes = JsonExtractionSupport.extractObjects(
            "[{\"elementId\":\"e1\"},{\"elementId\":\"e2\"}]", mapper);
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).path("elementId").asText()).isEqualTo("e1");
        assertThat(nodes.get(1).path("elementId").asText()).isEqualTo("e2");
    }

    @Test
    void extractObjects_markdownFencedAndCommentary_stillParses() {
        String raw = "Here is the result:\n```json\n[{\"verdict\":\"APPROVE\",\"comments\":[]}]\n```\nDone.";
        List<JsonNode> nodes = JsonExtractionSupport.extractObjects(raw, mapper);
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).path("verdict").asText()).isEqualTo("APPROVE");
    }

    @Test
    void extractObjects_nestedObjectsKeptWithinParent() {
        String raw = "{\"elementId\":\"e1\",\"vocabConcepts\":[{\"conceptIri\":\"x\"}]}";
        List<JsonNode> nodes = JsonExtractionSupport.extractObjects(raw, mapper);
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).path("vocabConcepts")).hasSize(1);
        assertThat(nodes.get(0).path("vocabConcepts").get(0).path("conceptIri").asText()).isEqualTo("x");
    }

    @Test
    void extractObjects_escapedQuotesInStrings_doNotBreakBraceMatching() {
        String raw = "[{\"issue\":\"contains \\\"quoted\\\" braces } here\"}]";
        List<JsonNode> nodes = JsonExtractionSupport.extractObjects(raw, mapper);
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).path("issue").asText()).contains("quoted");
    }

    @Test
    void extractObjects_blankOrNull_returnsEmpty() {
        assertThat(JsonExtractionSupport.extractObjects(null, mapper)).isEmpty();
        assertThat(JsonExtractionSupport.extractObjects("   ", mapper)).isEmpty();
    }
}
