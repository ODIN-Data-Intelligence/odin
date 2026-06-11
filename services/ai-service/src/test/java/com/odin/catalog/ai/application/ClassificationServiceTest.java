package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.ClassifyElementsRequest;
import com.odin.catalog.ai.api.v1.dto.ClassifyElementsRequest.ElementInput;
import com.odin.catalog.ai.api.v1.dto.ClassifyElementsResponse;
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
class ClassificationServiceTest {

    @Mock ChatClient chatClient;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks ClassificationService service;

    // ── classify ──────────────────────────────────────────────────────────

    @Test
    void classify_nullElements_returnsEmpty() {
        var result = service.classify(new ClassifyElementsRequest(null));
        assertThat(result.results()).isEmpty();
    }

    @Test
    void classify_emptyElements_returnsEmpty() {
        var result = service.classify(new ClassifyElementsRequest(List.of()));
        assertThat(result.results()).isEmpty();
    }

    @Test
    void classify_validJson_parsesResults() {
        stubAi("[{\"elementId\":\"e1\",\"classification\":\"CONFIDENTIAL\",\"reasoning\":\"personal data\"}]");

        var req = new ClassifyElementsRequest(List.of(elem("e1", "customer_email")));
        ClassifyElementsResponse resp = service.classify(req);

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).elementId()).isEqualTo("e1");
        assertThat(resp.results().get(0).classification()).isEqualTo("CONFIDENTIAL");
        assertThat(resp.results().get(0).reasoning()).isEqualTo("personal data");
    }

    @Test
    void classify_markdownFencedJson_parsesResults() {
        stubAi("```json\n[{\"elementId\":\"e2\",\"classification\":\"PUBLIC\",\"reasoning\":\"public price\"}]\n```");

        var req = new ClassifyElementsRequest(List.of(elem("e2", "market_price")));
        ClassifyElementsResponse resp = service.classify(req);

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).elementId()).isEqualTo("e2");
        assertThat(resp.results().get(0).classification()).isEqualTo("PUBLIC");
    }

    @Test
    void classify_invalidClassificationLevel_filteredOut() {
        stubAi("[{\"elementId\":\"e3\",\"classification\":\"UNKNOWN_LEVEL\",\"reasoning\":\"test\"}]");

        var req = new ClassifyElementsRequest(List.of(elem("e3", "some_field")));
        ClassifyElementsResponse resp = service.classify(req);

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void classify_allFourValidLevels_allParsed() {
        stubAi("[" +
            "{\"elementId\":\"e1\",\"classification\":\"PUBLIC\",\"reasoning\":\"r\"}," +
            "{\"elementId\":\"e2\",\"classification\":\"INTERNAL\",\"reasoning\":\"r\"}," +
            "{\"elementId\":\"e3\",\"classification\":\"CONFIDENTIAL\",\"reasoning\":\"r\"}," +
            "{\"elementId\":\"e4\",\"classification\":\"HIGH_CONFIDENTIAL\",\"reasoning\":\"r\"}" +
            "]");

        var req = new ClassifyElementsRequest(List.of(
            elem("e1", "a"), elem("e2", "b"), elem("e3", "c"), elem("e4", "d")));
        ClassifyElementsResponse resp = service.classify(req);

        assertThat(resp.results()).hasSize(4);
    }

    @Test
    void classify_aiThrowsCompletionException_returnsEmpty() {
        ChatClient.ChatClientRequestSpec req = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(req);
        when(req.system(anyString())).thenReturn(req);
        when(req.user(anyString())).thenReturn(req);
        when(req.call()).thenReturn(call);
        when(call.content()).thenThrow(new RuntimeException("AI timeout"));

        var result = service.classify(new ClassifyElementsRequest(List.of(elem("e1", "field"))));

        assertThat(result.results()).isEmpty();
    }

    @Test
    void classify_blankJsonResponse_returnsEmpty() {
        stubAi("   ");

        var req = new ClassifyElementsRequest(List.of(elem("e1", "field")));
        ClassifyElementsResponse resp = service.classify(req);

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void classify_jsonWithMissingElementId_filteredOut() {
        stubAi("[{\"classification\":\"PUBLIC\",\"reasoning\":\"test\"}]");

        var req = new ClassifyElementsRequest(List.of(elem("e1", "field")));
        ClassifyElementsResponse resp = service.classify(req);

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void classify_jsonWithEscapedStrings_parsedCorrectly() {
        stubAi("[{\"elementId\":\"e1\",\"classification\":\"CONFIDENTIAL\",\"reasoning\":\"contains \\\"quoted\\\" text\"}]");

        var req = new ClassifyElementsRequest(List.of(elem("e1", "field")));
        ClassifyElementsResponse resp = service.classify(req);

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).reasoning()).contains("quoted");
    }

    @Test
    void classify_elementWithAllOptionalFields_builds() {
        stubAi("[{\"elementId\":\"e1\",\"classification\":\"INTERNAL\",\"reasoning\":\"ops data\"}]");

        ElementInput el = new ElementInput("e1", "trade_id", "Trade Identifier", "String",
            "Unique trade identifier", List.of("https://fibo.org/trade"), List.of("Trade"));
        ClassifyElementsResponse resp = service.classify(new ClassifyElementsRequest(List.of(el)));

        assertThat(resp.results()).hasSize(1);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private void stubAi(String response) {
        ChatClient.ChatClientRequestSpec req = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(req);
        when(req.system(anyString())).thenReturn(req);
        when(req.user(anyString())).thenReturn(req);
        when(req.call()).thenReturn(call);
        when(call.content()).thenReturn(response);
    }

    private ElementInput elem(String id, String name) {
        return new ElementInput(id, name, null, null, null, null, null);
    }
}
