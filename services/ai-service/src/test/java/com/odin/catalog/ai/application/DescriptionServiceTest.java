package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.ClassifyElementsRequest;
import com.odin.catalog.ai.api.v1.dto.ClassifyElementsRequest.ElementInput;
import com.odin.catalog.ai.api.v1.dto.DescribeElementsResponse;
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
class DescriptionServiceTest {

    @Mock ChatClient chatClient;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks DescriptionService service;

    // ── describe ──────────────────────────────────────────────────────────

    @Test
    void describe_nullElements_returnsEmpty() {
        var result = service.describe(new ClassifyElementsRequest(null));
        assertThat(result.results()).isEmpty();
    }

    @Test
    void describe_emptyElements_returnsEmpty() {
        var result = service.describe(new ClassifyElementsRequest(List.of()));
        assertThat(result.results()).isEmpty();
    }

    @Test
    void describe_validJson_parsesResults() {
        stubAi("[{\"elementId\":\"e1\",\"description\":\"The amount of the trade.\",\"reasoning\":\"Based on FIBO MonetaryAmount.\"}]");

        var req = new ClassifyElementsRequest(List.of(elem("e1", "trade_amount")));
        DescribeElementsResponse resp = service.describe(req);

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).elementId()).isEqualTo("e1");
        assertThat(resp.results().get(0).description()).isEqualTo("The amount of the trade.");
        assertThat(resp.results().get(0).reasoning()).contains("FIBO");
    }

    @Test
    void describe_markdownFenced_parsesResults() {
        stubAi("```json\n[{\"elementId\":\"e2\",\"description\":\"The price at execution.\",\"reasoning\":\"Derived from schema.org Price.\"}]\n```");

        var req = new ClassifyElementsRequest(List.of(elem("e2", "execution_price")));
        DescribeElementsResponse resp = service.describe(req);

        assertThat(resp.results()).hasSize(1);
        assertThat(resp.results().get(0).description()).contains("price");
    }

    @Test
    void describe_missingElementId_filteredOut() {
        stubAi("[{\"description\":\"some description\",\"reasoning\":\"some reason\"}]");

        var req = new ClassifyElementsRequest(List.of(elem("e1", "field")));
        DescribeElementsResponse resp = service.describe(req);

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void describe_blankDescription_filteredOut() {
        stubAi("[{\"elementId\":\"e1\",\"description\":\"   \",\"reasoning\":\"reason\"}]");

        var req = new ClassifyElementsRequest(List.of(elem("e1", "field")));
        DescribeElementsResponse resp = service.describe(req);

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void describe_aiThrowsCompletionException_returnsEmpty() {
        ChatClient.ChatClientRequestSpec req = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(req);
        when(req.system(anyString())).thenReturn(req);
        when(req.user(anyString())).thenReturn(req);
        when(req.call()).thenReturn(call);
        when(call.content()).thenThrow(new RuntimeException("AI timeout"));

        var result = service.describe(new ClassifyElementsRequest(List.of(elem("e1", "field"))));

        assertThat(result.results()).isEmpty();
    }

    @Test
    void describe_blankResponse_returnsEmpty() {
        stubAi("   ");

        var req = new ClassifyElementsRequest(List.of(elem("e1", "field")));
        DescribeElementsResponse resp = service.describe(req);

        assertThat(resp.results()).isEmpty();
    }

    @Test
    void describe_multipleElements_allParsed() {
        stubAi("[" +
            "{\"elementId\":\"e1\",\"description\":\"Customer identifier.\",\"reasoning\":\"schema.org Customer\"}," +
            "{\"elementId\":\"e2\",\"description\":\"Transaction date.\",\"reasoning\":\"schema.org Date\"}" +
            "]");

        var req = new ClassifyElementsRequest(List.of(elem("e1", "customer_id"), elem("e2", "tx_date")));
        DescribeElementsResponse resp = service.describe(req);

        assertThat(resp.results()).hasSize(2);
    }

    @Test
    void describe_elementWithVocabConcepts_includesInPrompt() {
        stubAi("[{\"elementId\":\"e1\",\"description\":\"A monetary amount.\",\"reasoning\":\"FIBO MonetaryAmount\"}]");

        ElementInput el = new ElementInput("e1", "notional", "Notional Amount", "Decimal",
            "Trade notional value",
            List.of("https://spec.edmcouncil.org/fibo/ontology/FND/MonetaryAmount"),
            List.of("MonetaryAmount"));
        DescribeElementsResponse resp = service.describe(new ClassifyElementsRequest(List.of(el)));

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
