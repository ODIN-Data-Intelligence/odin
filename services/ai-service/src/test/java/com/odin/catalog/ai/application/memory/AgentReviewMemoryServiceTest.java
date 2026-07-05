package com.odin.catalog.ai.application.memory;

import com.odin.catalog.ai.api.v1.dto.AgenticEvent.CombinedProposal;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent.ElementProposal;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent.ReviewComment;
import com.odin.catalog.ai.client.CatalogServiceClient.LogicalElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentReviewMemoryServiceTest {

    @Mock VectorStore vectorStore;

    private final String tenantId = "11111111-1111-1111-1111-111111111111";
    private final String elementId = "el-1";

    private AgentReviewMemoryService service(boolean enabled) {
        return new AgentReviewMemoryService(vectorStore, enabled, 3);
    }

    private LogicalElement element() {
        return new LogicalElement(elementId, "customer_email", "Customer Email", null, "String",
            false, null, false, false, List.of());
    }

    private CombinedProposal proposal() {
        return new CombinedProposal(List.of(new ElementProposal(
            elementId, "Customer Email", "The customer's email.", "reasoning",
            "CONFIDENTIAL", "personal data", List.of(), true, true, "identifies a person")));
    }

    @SuppressWarnings("unchecked")
    private List<Document> captureAdded() {
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        return captor.getValue();
    }

    @Test
    void record_onApproval_writesLessonAndExemplar() {
        Map<String, List<ReviewComment>> issues = Map.of(elementId,
            List.of(new ReviewComment(elementId, "classification", "raise to CONFIDENTIAL")));

        service(true).record(tenantId, List.of(element()), List.of("trade"), proposal(), issues, true);

        List<Document> docs = captureAdded();
        assertThat(docs).extracting(d -> d.getMetadata().get("memoryKind"))
            .containsExactlyInAnyOrder("LESSON", "EXEMPLAR");
        Document exemplar = docs.stream()
            .filter(d -> "EXEMPLAR".equals(d.getMetadata().get("memoryKind"))).findFirst().orElseThrow();
        assertThat(exemplar.getMetadata()).containsEntry("entityType", "AGENT_MEMORY")
            .containsEntry("tenantId", tenantId)
            .containsEntry("classification", "CONFIDENTIAL");
        assertThat(exemplar.getText()).contains("customer_email").contains("CONFIDENTIAL");
    }

    @Test
    void record_onMaxReached_writesLessonButNoExemplar() {
        Map<String, List<ReviewComment>> issues = Map.of(elementId,
            List.of(new ReviewComment(elementId, "classification", "raise to CONFIDENTIAL")));

        service(true).record(tenantId, List.of(element()), List.of("trade"), proposal(), issues, false);

        List<Document> docs = captureAdded();
        assertThat(docs).extracting(d -> d.getMetadata().get("memoryKind")).containsExactly("LESSON");
    }

    @Test
    void record_disabled_writesNothing() {
        service(false).record(tenantId, List.of(element()), List.of("trade"), proposal(), Map.of(), true);
        verify(vectorStore, never()).add(any());
    }

    @Test
    void retrieveLessons_returnsDedupedTexts() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(List.of(new Document("lesson A"), new Document("lesson A"), new Document("lesson B")));

        List<String> lessons = service(true).retrieveLessons(tenantId, List.of(element()), List.of("trade"));

        assertThat(lessons).containsExactly("lesson A", "lesson B");
    }

    @Test
    void retrieveLessons_disabledOrNoTenant_returnsEmpty() {
        assertThat(service(false).retrieveLessons(tenantId, List.of(element()), List.of())).isEmpty();
        assertThat(service(true).retrieveLessons(null, List.of(element()), List.of())).isEmpty();
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }
}
