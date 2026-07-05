package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent;
import com.odin.catalog.ai.application.memory.AgentReviewMemoryService;
import com.odin.catalog.ai.client.CatalogServiceClient;
import com.odin.catalog.ai.client.CatalogServiceClient.AgenticRecommendationsPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgenticReviewServiceTest {

    @Mock ChatClient chatClient;
    @Mock CatalogServiceClient catalogClient;
    @Mock VectorStore vectorStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID datasetId = UUID.randomUUID();
    private final UUID modelId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final String elementId = UUID.randomUUID().toString();

    /** Captured proposer/reviewer prompts (every chatClient.user(...) call), in order. */
    private ChatClient.ChatClientRequestSpec reqSpec;

    private AgenticReviewService service() {
        AgentReviewMemoryService memory = new AgentReviewMemoryService(vectorStore, true, 3);
        return new AgenticReviewService(chatClient, objectMapper, catalogClient, memory);
    }

    private void stubContext() {
        when(catalogClient.getDataset(eq(datasetId.toString()), anyString()))
            .thenReturn(new CatalogServiceClient.DatasetSummary(
                "d", "Trades", "Trade book", List.of("trade"), List.of(), tenantId.toString()));
        when(catalogClient.getLogicalElements(eq(modelId.toString()), anyString()))
            .thenReturn(List.of(new CatalogServiceClient.LogicalElement(
                elementId, "customer_email", "Customer Email", null, "String", false, null, false, false, List.of())));
        when(catalogClient.getDistributions(eq(datasetId.toString()), anyString())).thenReturn(List.of());
        when(catalogClient.getVocabularies(anyString())).thenReturn(List.of());
    }

    /** Stubs the LLM to return the given responses across successive call().content() invocations. */
    private void stubLlm(String first, String... rest) {
        reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(reqSpec);
        when(reqSpec.system(anyString())).thenReturn(reqSpec);
        when(reqSpec.user(anyString())).thenReturn(reqSpec);
        when(reqSpec.call()).thenReturn(call);
        when(call.content()).thenReturn(first, rest);
    }

    /** The user-prompt strings passed to the LLM, in call order (proposer/reviewer interleaved). */
    private List<String> capturedPrompts() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(reqSpec, atLeastOnce()).user(captor.capture());
        return captor.getAllValues();
    }

    private List<AgenticEvent> collect(AgenticReviewService service) {
        List<String> raw = service.review(datasetId, modelId, "Bearer t")
            .collectList().block(Duration.ofSeconds(30));
        assertThat(raw).isNotNull();
        return raw.stream().map(s -> {
            try { return objectMapper.readValue(s, AgenticEvent.class); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).toList();
    }

    private String proposal(String classification, boolean pii) {
        return "[{\"elementId\":\"" + elementId + "\",\"description\":\"The customer's email address.\","
            + "\"descriptionReasoning\":\"From schema.org email.\",\"classification\":\"" + classification + "\","
            + "\"classificationReasoning\":\"personal contact data\","
            + "\"vocabConcepts\":[{\"conceptIri\":\"https://schema.org/email\",\"conceptLabel\":\"email\",\"matchType\":\"exactMatch\",\"reasoning\":\"is an email\"}],"
            + "\"isPersonalInformation\":" + pii + ",\"isDirectIdentifier\":" + pii + ",\"piiReasoning\":\"email identifies a person\"}]";
    }

    private String reject(String dimension, String issue) {
        return "{\"verdict\":\"REJECT\",\"summary\":\"needs work\",\"comments\":[{\"elementId\":\""
            + elementId + "\",\"dimension\":\"" + dimension + "\",\"issue\":\"" + issue + "\"}]}";
    }

    @Test
    void review_approveFirstIteration_emitsDoneAndPersists() {
        stubContext();
        stubLlm(
            proposal("CONFIDENTIAL", true),
            "{\"verdict\":\"APPROVE\",\"summary\":\"looks good\",\"comments\":[]}");

        List<AgenticEvent> events = collect(service());

        assertThat(events).extracting(AgenticEvent::phase)
            .containsExactly("CONTEXT", "PROPOSING", "PROPOSAL", "REVIEWING", "REVIEW", "DONE");

        AgenticEvent proposalEvent = events.stream().filter(e -> "PROPOSAL".equals(e.phase())).findFirst().orElseThrow();
        assertThat(proposalEvent.proposal().elements()).hasSize(1);
        assertThat(proposalEvent.proposal().elements().get(0).classification()).isEqualTo("CONFIDENTIAL");
        assertThat(proposalEvent.proposal().elements().get(0).vocabConcepts()).hasSize(1);

        AgenticEvent done = events.get(events.size() - 1);
        assertThat(done.proposal().elements()).hasSize(1);

        ArgumentCaptor<AgenticRecommendationsPayload> captor = ArgumentCaptor.forClass(AgenticRecommendationsPayload.class);
        verify(catalogClient).applyAgenticRecommendations(eq(modelId.toString()), captor.capture(), eq("Bearer t"));
        assertThat(captor.getValue().elements()).hasSize(1);
        assertThat(captor.getValue().elements().get(0).isPersonalInformation()).isTrue();

        // On approval, memory is recorded (lesson/exemplar docs added to the vector store).
        verify(vectorStore, atLeastOnce()).add(any());
    }

    @Test
    void review_rejectThenApprove_loopsAndFeedsBackComments() {
        stubContext();
        stubLlm(
            proposal("INTERNAL", true),                                   // iter 1 proposer (inconsistent: PII but INTERNAL)
            reject("classification", "raise to CONFIDENTIAL"),
            proposal("CONFIDENTIAL", true),                               // iter 2 proposer (fixed)
            "{\"verdict\":\"APPROVE\",\"summary\":\"ok\",\"comments\":[]}");

        List<AgenticEvent> events = collect(service());

        // After iter 1 flags only classification, the upstream dimensions (vocab, pii) lock.
        assertThat(events).extracting(AgenticEvent::phase).containsExactly(
            "CONTEXT", "PROPOSING", "PROPOSAL", "REVIEWING", "REVIEW", "LOCKED",
            "PROPOSING", "PROPOSAL", "REVIEWING", "REVIEW", "DONE");

        AgenticEvent locked = events.stream().filter(e -> "LOCKED".equals(e.phase())).findFirst().orElseThrow();
        assertThat(locked.message()).contains("vocab").contains("pii");

        AgenticEvent firstReview = events.stream().filter(e -> "REVIEW".equals(e.phase())).findFirst().orElseThrow();
        assertThat(firstReview.verdict()).isEqualTo("REJECT");
        assertThat(firstReview.comments()).hasSize(1);

        verify(catalogClient, times(1)).applyAgenticRecommendations(anyString(), any(), anyString());
    }

    @Test
    void review_accumulatesFeedbackAcrossIterations_noRepeatSubmission() {
        stubContext();
        // Both issues stay on the SAME frontier dimension (classification): a non-flagged dimension
        // would otherwise lock, so keeping classification contested exercises ledger accumulation.
        stubLlm(
            proposal("INTERNAL", true),                       // iter 1 proposer
            reject("classification", "ISSUE_ALPHA"),          // iter 1 reviewer
            proposal("CONFIDENTIAL", true),                   // iter 2 proposer
            reject("classification", "ISSUE_BETA"),           // iter 2 reviewer
            proposal("HIGH_CONFIDENTIAL", true),              // iter 3 proposer
            "{\"verdict\":\"APPROVE\",\"summary\":\"ok\",\"comments\":[]}");

        collect(service());

        // Proposer prompts are at even indices (0,2,4): P1, R1, P2, R2, P3, R3.
        List<String> prompts = capturedPrompts();
        assertThat(prompts).hasSizeGreaterThanOrEqualTo(5);
        String thirdProposerPrompt = prompts.get(4);

        // The 3rd proposer prompt must carry the WHOLE rejection history, not just the latest comment.
        assertThat(thirdProposerPrompt).contains("ISSUE_ALPHA");
        assertThat(thirdProposerPrompt).contains("ISSUE_BETA");
        assertThat(thirdProposerPrompt).contains("does NOT repeat any rejected value");
    }

    @Test
    void review_lockedDimensionCannotBeReopened_terminatesEarly() {
        stubContext();
        stubLlm(
            proposal("CONFIDENTIAL", true),                   // iter 1 proposer
            reject("classification", "tighten classification"), // iter 1 reviewer (only classification → vocab/pii lock)
            proposal("CONFIDENTIAL", true),                   // iter 2 proposer
            reject("vocab", "change the IRI"));               // iter 2 reviewer tries to reopen LOCKED vocab

        List<AgenticEvent> events = collect(service());

        // The vocab comment is dropped (locked), leaving no open issues → DONE in 2 iterations, not MAX_REACHED.
        assertThat(events.get(events.size() - 1).phase()).isEqualTo("DONE");
        assertThat(events).extracting(AgenticEvent::phase).doesNotContain("MAX_REACHED");
        assertThat(events).filteredOn(e -> "REVIEW".equals(e.phase())).hasSize(2);
        assertThat(events).filteredOn(e -> "LOCKED".equals(e.phase())).isNotEmpty();
        verify(catalogClient, times(1)).applyAgenticRecommendations(anyString(), any(), anyString());
    }

    @Test
    void review_progressGuard_forceLocksStuckDimension_andConverges() {
        stubContext();
        // Reviewer keeps rejecting classification; the guard must force-lock it and reach DONE.
        stubLlm(
            proposal("INTERNAL", true), reject("classification", "still wrong"),   // iter 1
            proposal("CONFIDENTIAL", true), reject("classification", "still wrong"), // iter 2
            proposal("HIGH_CONFIDENTIAL", true), reject("classification", "still wrong"), // iter 3 → force-lock
            proposal("HIGH_CONFIDENTIAL", true), reject("classification", "still wrong")); // iter 4 → comment dropped → DONE

        List<AgenticEvent> events = collect(service());

        assertThat(events.get(events.size() - 1).phase()).isEqualTo("DONE");
        // Converged well under the 15-iteration absolute cap.
        assertThat(events).filteredOn(e -> "PROPOSING".equals(e.phase())).hasSizeLessThanOrEqualTo(5);
        assertThat(events).filteredOn(e -> "LOCKED".equals(e.phase()))
            .anySatisfy(e -> assertThat(e.message()).contains("forced"));
    }

    @Test
    void review_injectsLongTermLessons_intoFirstProposerPrompt_andEmitsMemoryEvent() {
        stubContext();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(List.of(new Document("LESSON_FROM_PAST: email fields must be CONFIDENTIAL")));
        stubLlm(
            proposal("CONFIDENTIAL", true),
            "{\"verdict\":\"APPROVE\",\"summary\":\"ok\",\"comments\":[]}");

        List<AgenticEvent> events = collect(service());

        // A MEMORY event is surfaced for UI transparency.
        AgenticEvent memory = events.stream().filter(e -> "MEMORY".equals(e.phase())).findFirst().orElseThrow();
        assertThat(memory.message()).contains("lesson");

        // The retrieved lesson is injected into the first proposer prompt.
        String firstProposerPrompt = capturedPrompts().get(0);
        assertThat(firstProposerPrompt).contains("LESSON_FROM_PAST");
        assertThat(firstProposerPrompt).contains("Lessons from prior reviews");
    }

    @Test
    void review_memoryFailure_doesNotBreakReview() {
        stubContext();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenThrow(new RuntimeException("vector store down"));
        stubLlm(
            proposal("CONFIDENTIAL", true),
            "{\"verdict\":\"APPROVE\",\"summary\":\"ok\",\"comments\":[]}");

        List<AgenticEvent> events = collect(service());

        // Review still completes and persists despite the memory subsystem failing.
        assertThat(events.get(events.size() - 1).phase()).isEqualTo("DONE");
        verify(catalogClient).applyAgenticRecommendations(anyString(), any(), anyString());
    }

    @Test
    void review_noElements_emitsErrorAndDoesNotPersist() {
        when(catalogClient.getDataset(anyString(), anyString())).thenReturn(null);
        when(catalogClient.getLogicalElements(anyString(), anyString())).thenReturn(List.of());
        when(catalogClient.getDistributions(anyString(), anyString())).thenReturn(List.of());
        when(catalogClient.getVocabularies(anyString())).thenReturn(List.of());

        List<AgenticEvent> events = collect(service());

        assertThat(events).extracting(AgenticEvent::phase).containsExactly("CONTEXT", "ERROR");
        verify(catalogClient, never()).applyAgenticRecommendations(anyString(), any(), anyString());
    }
}
