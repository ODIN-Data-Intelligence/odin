package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationRequest;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationRequest.ElementVocabInput;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationRequest.VocabInfo;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VocabConceptRecommendationServiceTest {

    @Mock ChatClient chatClient;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks VocabConceptRecommendationService service;

    // ── recommend ─────────────────────────────────────────────────────────

    @Test
    void recommend_nullElements_returnsEmpty() {
        var result = service.recommend(new VocabConceptRecommendationRequest(null));
        assertThat(result.results()).isEmpty();
    }

    @Test
    void recommend_emptyElements_returnsEmpty() {
        var result = service.recommend(new VocabConceptRecommendationRequest(List.of()));
        assertThat(result.results()).isEmpty();
    }

    @Test
    void recommend_validJson_parsesConcepts() {
        stubAi("[{\"elementId\":\"e1\",\"conceptIri\":\"https://fibo.org/MonetaryAmount\"," +
            "\"conceptLabel\":\"MonetaryAmount\",\"conceptDefinition\":\"A monetary amount\"," +
            "\"matchType\":\"exactMatch\",\"reasoning\":\"trade notional is a monetary amount\"}]");

        var resp = service.recommend(req("e1", "trade_notional", List.of()));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).elementId()).isEqualTo("e1");
        assertThat(resp.results().get(0).concepts()).hasSize(1);
        assertThat(resp.results().get(0).concepts().get(0).conceptIri()).isEqualTo("https://fibo.org/MonetaryAmount");
        assertThat(resp.results().get(0).concepts().get(0).matchType()).isEqualTo("exactMatch");
    }

    @Test
    void recommend_markdownFenced_parsesResults() {
        stubAi("```json\n[{\"elementId\":\"e1\",\"conceptIri\":\"https://schema.org/name\"," +
            "\"conceptLabel\":\"name\",\"conceptDefinition\":\"A person's name\"," +
            "\"matchType\":\"closeMatch\",\"reasoning\":\"name field\"}]\n```");

        var resp = service.recommend(req("e1", "customer_name", List.of()));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).concepts().get(0).matchType()).isEqualTo("closeMatch");
    }

    @Test
    void recommend_unknownElementId_ignored() {
        stubAi("[{\"elementId\":\"unknown\",\"conceptIri\":\"https://fibo.org/Concept\"," +
            "\"conceptLabel\":\"Concept\",\"conceptDefinition\":\"Def\"," +
            "\"matchType\":\"exactMatch\",\"reasoning\":\"r\"}]");

        var resp = service.recommend(req("e1", "field", List.of()));

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void recommend_maxFiveSuggestionsPerElement() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 7; i++) {
            if (i > 0) json.append(",");
            json.append("{\"elementId\":\"e1\",\"conceptIri\":\"https://fibo.org/C").append(i)
                .append("\",\"conceptLabel\":\"C").append(i)
                .append("\",\"conceptDefinition\":\"d\",\"matchType\":\"exactMatch\",\"reasoning\":\"r\"}");
        }
        json.append("]");
        stubAi(json.toString());

        var resp = service.recommend(req("e1", "field", List.of()));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).concepts()).hasSize(5);
    }

    @Test
    void recommend_aiThrowsCompletionException_returnsEmpty() {
        ChatClient.ChatClientRequestSpec r = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(r);
        when(r.system(anyString())).thenReturn(r);
        when(r.user(anyString())).thenReturn(r);
        when(r.call()).thenReturn(call);
        when(call.content()).thenThrow(new RuntimeException("AI timeout"));

        var result = service.recommend(req("e1", "field", List.of()));

        assertThat(result.results()).isEmpty();
    }

    @Test
    void recommend_conceptWithNullIri_ignored() {
        stubAi("[{\"elementId\":\"e1\",\"conceptIri\":null," +
            "\"conceptLabel\":\"Label\",\"conceptDefinition\":\"Def\"," +
            "\"matchType\":\"exactMatch\",\"reasoning\":\"r\"}]");

        var resp = service.recommend(req("e1", "field", List.of()));

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void recommend_withVocabInfo_includesInPrompt() {
        stubAi("[{\"elementId\":\"e1\",\"conceptIri\":\"https://w3id.org/dpv/dpv-pd#EmailAddress\"," +
            "\"conceptLabel\":\"EmailAddress\",\"conceptDefinition\":\"Email address\"," +
            "\"matchType\":\"exactMatch\",\"reasoning\":\"email field\"}]");

        VocabInfo vocab = new VocabInfo("dpv-pd", "https://w3id.org/dpv/dpv-pd#", "DPV Personal Data",
            "EmailAddress, Name, NationalIdentificationNumber");
        ElementVocabInput el = new ElementVocabInput("e1", "email", "Email Address", "String",
            "Customer email", null, null, List.of(vocab));

        var resp = service.recommend(new VocabConceptRecommendationRequest(List.of(el)));

        assertThat(resp.results()).hasSize(1);
    }

    @Test
    void recommend_multipleElementsSomeWithConcepts() {
        stubAi("[" +
            "{\"elementId\":\"e1\",\"conceptIri\":\"https://fibo.org/C1\",\"conceptLabel\":\"C1\"," +
            "\"conceptDefinition\":\"d\",\"matchType\":\"exactMatch\",\"reasoning\":\"r\"}," +
            "{\"elementId\":\"e2\",\"conceptIri\":\"https://fibo.org/C2\",\"conceptLabel\":\"C2\"," +
            "\"conceptDefinition\":\"d\",\"matchType\":\"closeMatch\",\"reasoning\":\"r\"}" +
            "]");

        ElementVocabInput e1 = new ElementVocabInput("e1", "trade_amount", null, null, null, null, null, null);
        ElementVocabInput e2 = new ElementVocabInput("e2", "currency", null, null, null, null, null, null);

        var resp = service.recommend(new VocabConceptRecommendationRequest(List.of(e1, e2)));

        assertThat(resp.results()).hasSize(2);
    }

    @Test
    void recommend_elementWithExistingMappings_includedInPrompt() {
        stubAi("[{\"elementId\":\"e1\",\"conceptIri\":\"https://fibo.org/NewConcept\"," +
            "\"conceptLabel\":\"NewConcept\",\"conceptDefinition\":\"New\"," +
            "\"matchType\":\"relatedMatch\",\"reasoning\":\"additional match\"}]");

        ElementVocabInput el = new ElementVocabInput("e1", "trade_id", "Trade ID", "String", null,
            List.of("https://fibo.org/ExistingConcept"), List.of("ExistingConcept"), null);

        var resp = service.recommend(new VocabConceptRecommendationRequest(List.of(el)));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).concepts().get(0).matchType()).isEqualTo("relatedMatch");
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private void stubAi(String response) {
        ChatClient.ChatClientRequestSpec r = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(r);
        when(r.system(anyString())).thenReturn(r);
        when(r.user(anyString())).thenReturn(r);
        when(r.call()).thenReturn(call);
        when(call.content()).thenReturn(response);
    }

    private VocabConceptRecommendationRequest req(String id, String name, List<VocabInfo> vocabs) {
        ElementVocabInput el = new ElementVocabInput(id, name, null, null, null, null, null,
            vocabs.isEmpty() ? null : vocabs);
        return new VocabConceptRecommendationRequest(List.of(el));
    }
}
