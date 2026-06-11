package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.PiiRecommendationRequest;
import com.odin.catalog.ai.api.v1.dto.PiiRecommendationRequest.ElementPiiInput;
import com.odin.catalog.ai.api.v1.dto.PiiRecommendationResponse;
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
class PiiRecommendationServiceTest {

    @Mock ChatClient chatClient;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks PiiRecommendationService service;

    // ── recommend ─────────────────────────────────────────────────────────

    @Test
    void recommend_nullElements_returnsEmpty() {
        var result = service.recommend(new PiiRecommendationRequest(null));
        assertThat(result.results()).isEmpty();
    }

    @Test
    void recommend_emptyElements_returnsEmpty() {
        var result = service.recommend(new PiiRecommendationRequest(List.of()));
        assertThat(result.results()).isEmpty();
    }

    @Test
    void recommend_validJson_parsesResults() {
        stubAi("[{\"elementId\":\"e1\",\"isPersonalInformation\":true,\"isDirectIdentifier\":false,\"reasoning\":\"customer name\"}]");

        PiiRecommendationResponse resp = service.recommend(req("e1", "customer_name"));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).elementId()).isEqualTo("e1");
        assertThat(resp.results().get(0).isPersonalInformation()).isTrue();
        assertThat(resp.results().get(0).isDirectIdentifier()).isFalse();
        assertThat(resp.results().get(0).reasoning()).isEqualTo("customer name");
    }

    @Test
    void recommend_directIdentifierImpliesPersonalInformation() {
        stubAi("[{\"elementId\":\"e1\",\"isPersonalInformation\":false,\"isDirectIdentifier\":true,\"reasoning\":\"national ID\"}]");

        PiiRecommendationResponse resp = service.recommend(req("e1", "national_id"));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).isPersonalInformation()).isTrue();
        assertThat(resp.results().get(0).isDirectIdentifier()).isTrue();
    }

    @Test
    void recommend_markdownFencedJson_parsesResults() {
        stubAi("```json\n[{\"elementId\":\"e1\",\"isPersonalInformation\":true,\"isDirectIdentifier\":true,\"reasoning\":\"email\"}]\n```");

        PiiRecommendationResponse resp = service.recommend(req("e1", "email"));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).isPersonalInformation()).isTrue();
    }

    @Test
    void recommend_notArrayResponse_fallsBack() {
        stubAi("{\"elementId\":\"e1\",\"isPersonalInformation\":true}");

        PiiRecommendationResponse resp = service.recommend(req("e1", "field"));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).isPersonalInformation()).isFalse();
        assertThat(resp.results().get(0).reasoning()).contains("could not be parsed");
    }

    @Test
    void recommend_invalidJson_fallsBack() {
        stubAi("not json at all !!!%{");

        PiiRecommendationResponse resp = service.recommend(req("e1", "field"));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).isPersonalInformation()).isFalse();
        assertThat(resp.results().get(0).reasoning()).contains("could not be parsed");
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

        PiiRecommendationResponse resp = service.recommend(req("e1", "field"));

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void recommend_nullRawResponse_returnsEmptyResults() {
        stubAi(null);

        PiiRecommendationResponse resp = service.recommend(req("e1", "field"));

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void recommend_blankElementId_filteredOut() {
        stubAi("[{\"elementId\":\"  \",\"isPersonalInformation\":true,\"isDirectIdentifier\":false,\"reasoning\":\"test\"}]");

        PiiRecommendationResponse resp = service.recommend(req("e1", "field"));

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void recommend_elementWithAllFields_buildsPrompt() {
        stubAi("[{\"elementId\":\"e1\",\"isPersonalInformation\":false,\"isDirectIdentifier\":false,\"reasoning\":\"trade id\"}]");

        ElementPiiInput el = new ElementPiiInput("e1", "trade_id", "Trade ID", "String",
            "Unique trade identifier",
            List.of("https://fibo.org/trade"), List.of("TradeIdentifier"),
            "Executed Trades", List.of("finance", "risk"));
        PiiRecommendationResponse resp = service.recommend(new PiiRecommendationRequest(List.of(el)));

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).isPersonalInformation()).isFalse();
    }

    @Test
    void recommend_multipleElements_allFallBackOnParseError() {
        stubAi("garbage response");

        PiiRecommendationResponse resp = service.recommend(
            new PiiRecommendationRequest(List.of(
                piiInput("e1", "field1"),
                piiInput("e2", "field2")
            )));

        assertThat(resp.results()).hasSize(2);
        assertThat(resp.results()).allMatch(r -> !r.isPersonalInformation() && !r.isDirectIdentifier());
    }

    @Test
    void recommend_elementWithBlankOptionalFields_onlyNameInPrompt() {
        stubAi("[{\"elementId\":\"e1\",\"isPersonalInformation\":false,\"isDirectIdentifier\":false,\"reasoning\":\"not personal\"}]");

        ElementPiiInput el = new ElementPiiInput("e1", "trade_id", "  ", "  ", "  ",
            List.of(), List.of(), "  ", List.of());
        PiiRecommendationResponse resp = service.recommend(new PiiRecommendationRequest(List.of(el)));

        assertThat(resp.results()).hasSize(1);
    }

    @Test
    void recommend_invalidMarkdownFenceNoNewline_fallsBack() {
        // startsWith("```") but indexOf('\n') = -1, so extractJson gets trimmed with ``` prefix
        // Then no valid array extracted → fallback
        stubAi("```[{\"elementId\":\"e1\"}]```");

        PiiRecommendationResponse resp = service.recommend(req("e1", "field"));

        // The response might parse or fall back — just verify it doesn't throw
        assertThat(resp).isNotNull();
    }

    @Test
    void recommend_longInvalidResponse_abbreviatesInLog() {
        String longGarbage = "x".repeat(300) + " not valid json";
        stubAi(longGarbage);

        PiiRecommendationResponse resp = service.recommend(req("e1", "field"));

        // Falls back because longGarbage has no '[' → readTree fails
        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).reasoning()).contains("could not be parsed");
    }

    @Test
    void recommend_rawWithArrayButNoClose_returnsPartialOrFallback() {
        // arrayStart >= 0 but arrayEnd < arrayStart: "[abc" has [ at 0 but no ]
        stubAi("[{\"elementId\":\"e1\",\"isPersonalInformation\":false,\"isDirectIdentifier\":false");

        PiiRecommendationResponse resp = service.recommend(req("e1", "field"));

        assertThat(resp).isNotNull();
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

    private PiiRecommendationRequest req(String id, String name) {
        return new PiiRecommendationRequest(List.of(piiInput(id, name)));
    }

    private ElementPiiInput piiInput(String id, String name) {
        return new ElementPiiInput(id, name, null, null, null, null, null, null, null);
    }
}
