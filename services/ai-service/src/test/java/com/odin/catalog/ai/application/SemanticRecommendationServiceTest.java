package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.SemanticRecommendationRequest;
import com.odin.catalog.ai.api.v1.dto.SemanticRecommendationResponse;
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
class SemanticRecommendationServiceTest {

    @Mock ChatClient chatClient;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks SemanticRecommendationService service;

    // ── recommend ─────────────────────────────────────────────────────────

    @Test
    void recommend_validJson_parsesTypes() {
        stubAi("[{\"type\":\"Trade\",\"rationale\":\"Element names indicate trade executions.\",\"vocabularyHint\":\"https://fibo.org/trade\"}]");

        SemanticRecommendationResponse resp = service.recommend(minimalReq("ds-1", "Executed Trades"));

        assertThat(resp.types()).hasSize(1);
        assertThat(resp.types().get(0).type()).isEqualTo("Trade");
        assertThat(resp.types().get(0).rationale()).contains("trade executions");
        assertThat(resp.types().get(0).vocabularyHint()).isEqualTo("https://fibo.org/trade");
    }

    @Test
    void recommend_emptyVocabHint_nullInResult() {
        stubAi("[{\"type\":\"Customer\",\"rationale\":\"Contains customer data.\",\"vocabularyHint\":\"\"}]");

        SemanticRecommendationResponse resp = service.recommend(minimalReq("ds-1", "Customers"));

        assertThat(resp.types()).hasSize(1);
        assertThat(resp.types().get(0).vocabularyHint()).isNull();
    }

    @Test
    void recommend_multipleTypes_summaryIsPlural() {
        stubAi("[" +
            "{\"type\":\"Trade\",\"rationale\":\"r1\",\"vocabularyHint\":\"\"}," +
            "{\"type\":\"Position\",\"rationale\":\"r2\",\"vocabularyHint\":\"\"}" +
            "]");

        SemanticRecommendationResponse resp = service.recommend(minimalReq("ds-1", "Trades"));

        assertThat(resp.types()).hasSize(2);
        assertThat(resp.rationale()).contains("2 semantic type recommendations");
    }

    @Test
    void recommend_singleType_summaryIsSingular() {
        stubAi("[{\"type\":\"Payment\",\"rationale\":\"r\",\"vocabularyHint\":\"\"}]");

        SemanticRecommendationResponse resp = service.recommend(minimalReq("ds-1", "Payments"));

        assertThat(resp.types()).hasSize(1);
        assertThat(resp.rationale()).contains("1 semantic type recommendation");
        assertThat(resp.rationale()).doesNotContain("recommendations");
    }

    @Test
    void recommend_noTypes_summaryIsNoRecommendations() {
        stubAi("[]");

        SemanticRecommendationResponse resp = service.recommend(minimalReq("ds-1", "Unknown"));

        assertThat(resp.types()).isEmpty();
        assertThat(resp.rationale()).isEqualTo("No additional recommendations found.");
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

        SemanticRecommendationResponse resp = service.recommend(minimalReq("ds-1", "Test"));

        assertThat(resp.types()).isEmpty();
        assertThat(resp.rationale()).isEqualTo("Service unavailable");
    }

    @Test
    void recommend_nullTypeInJson_filteredOut() {
        stubAi("[{\"type\":null,\"rationale\":\"test\",\"vocabularyHint\":\"\"}]");

        SemanticRecommendationResponse resp = service.recommend(minimalReq("ds-1", "Test"));

        assertThat(resp.types()).isEmpty();
    }

    @Test
    void recommend_blankTypeInJson_filteredOut() {
        stubAi("[{\"type\":\"   \",\"rationale\":\"test\",\"vocabularyHint\":\"\"}]");

        SemanticRecommendationResponse resp = service.recommend(minimalReq("ds-1", "Test"));

        assertThat(resp.types()).isEmpty();
    }

    @Test
    void recommend_markdownFenced_parsesTypes() {
        stubAi("```\n[{\"type\":\"Account\",\"rationale\":\"account data\",\"vocabularyHint\":\"https://schema.org/BankAccount\"}]\n```");

        SemanticRecommendationResponse resp = service.recommend(minimalReq("ds-1", "Accounts"));

        assertThat(resp.types()).hasSize(1);
        assertThat(resp.types().get(0).type()).isEqualTo("Account");
    }

    @Test
    void recommend_withAllFields_buildsPrompt() {
        stubAi("[{\"type\":\"RiskMetric\",\"rationale\":\"risk data\",\"vocabularyHint\":\"\"}]");

        SemanticRecommendationRequest req = new SemanticRecommendationRequest(
            "ds-1", "Risk Positions", "Daily VaR data",
            List.of("risk", "var"), List.of("Finance"),
            List.of("trade_id", "var_amount"), List.of("String", "Decimal"),
            List.of("MonetaryAmount"), List.of("https://fibo.org/MonetaryAmount"));

        SemanticRecommendationResponse resp = service.recommend(req);

        assertThat(resp.types()).hasSize(1);
    }

    @Test
    void recommend_nullOptionalFields_handledGracefully() {
        stubAi("[{\"type\":\"Dataset\",\"rationale\":\"generic\",\"vocabularyHint\":\"\"}]");

        SemanticRecommendationRequest req = new SemanticRecommendationRequest(
            "ds-1", null, null, null, null, null, null, null, null);

        SemanticRecommendationResponse resp = service.recommend(req);

        assertThat(resp.types()).hasSize(1);
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

    private SemanticRecommendationRequest minimalReq(String datasetId, String title) {
        return new SemanticRecommendationRequest(datasetId, title, null, null, null, null, null, null, null);
    }
}
