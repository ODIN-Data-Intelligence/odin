package com.odin.catalog.ai.rag;

import com.odin.catalog.shared.kafka.consumer.KafkaEventConsumer;
import com.odin.catalog.shared.models.events.DatasetChangedPayload;
import com.odin.catalog.shared.models.events.KafkaEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock VectorStore vectorStore;
    @Mock KafkaEventConsumer kafkaEventConsumer;

    @InjectMocks EmbeddingService service;

    // ── upsert ────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void onDatasetChanged_upsert_addsChunksToVectorStore() throws Exception {
        DatasetChangedPayload payload = upsertPayload("ds-1", "Trade Positions",
            "Daily snapshot of open trade positions", List.of("risk", "finance"), List.of("Finance"));

        KafkaEnvelope<DatasetChangedPayload> envelope = mock(KafkaEnvelope.class);
        when(envelope.payload()).thenReturn(payload);
        when(kafkaEventConsumer.unwrap(any(), eq(DatasetChangedPayload.class))).thenReturn(envelope);

        service.onDatasetChanged(record());

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
        assertThat(captor.getValue().get(0).getText()).contains("Trade Positions");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onDatasetChanged_upsert_chunkZeroContainsTitleAndDescription() throws Exception {
        DatasetChangedPayload payload = upsertPayload("ds-2", "Equity Prices",
            "End-of-day equity prices", null, null);

        KafkaEnvelope<DatasetChangedPayload> envelope = mock(KafkaEnvelope.class);
        when(envelope.payload()).thenReturn(payload);
        when(kafkaEventConsumer.unwrap(any(), eq(DatasetChangedPayload.class))).thenReturn(envelope);

        service.onDatasetChanged(record());

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document chunk0 = captor.getValue().get(0);
        assertThat(chunk0.getText()).contains("Equity Prices");
        assertThat(chunk0.getText()).contains("End-of-day equity prices");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onDatasetChanged_upsertWithNullDataset_doesNotAddChunks() throws Exception {
        DatasetChangedPayload payload = mock(DatasetChangedPayload.class);
        when(payload.changeType()).thenReturn("UPDATED");
        when(payload.dataset()).thenReturn(null);

        KafkaEnvelope<DatasetChangedPayload> envelope = mock(KafkaEnvelope.class);
        when(envelope.payload()).thenReturn(payload);
        when(kafkaEventConsumer.unwrap(any(), eq(DatasetChangedPayload.class))).thenReturn(envelope);

        service.onDatasetChanged(record());

        verify(vectorStore, never()).add(any());
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void onDatasetChanged_delete_removesChunksFromVectorStore() throws Exception {
        DatasetChangedPayload payload = mock(DatasetChangedPayload.class);
        when(payload.changeType()).thenReturn("DELETED");
        when(payload.datasetId()).thenReturn("ds-3");

        KafkaEnvelope<DatasetChangedPayload> envelope = mock(KafkaEnvelope.class);
        when(envelope.payload()).thenReturn(payload);
        when(kafkaEventConsumer.unwrap(any(), eq(DatasetChangedPayload.class))).thenReturn(envelope);

        service.onDatasetChanged(record());

        verify(vectorStore).delete(any(List.class));
        verify(vectorStore, never()).add(any());
    }

    // ── error handling ────────────────────────────────────────────────────

    @Test
    void onDatasetChanged_deserializationFailure_doesNotThrow() throws Exception {
        when(kafkaEventConsumer.unwrap(any(), eq(DatasetChangedPayload.class)))
            .thenThrow(new RuntimeException("bad record"));

        service.onDatasetChanged(record());

        verifyNoInteractions(vectorStore);
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private DatasetChangedPayload upsertPayload(String id, String title, String description,
                                                 List<String> keywords, List<String> themes) {
        var resource = mock(com.odin.catalog.shared.models.dcat.DcatResource.class);
        when(resource.title()).thenReturn(title);
        when(resource.description()).thenReturn(description);
        when(resource.keywords()).thenReturn(keywords);
        when(resource.themes()).thenReturn(themes);

        var ds = mock(com.odin.catalog.shared.models.dcat.DcatDataset.class);
        when(ds.resource()).thenReturn(resource);

        DatasetChangedPayload payload = mock(DatasetChangedPayload.class);
        when(payload.changeType()).thenReturn("UPDATED");
        when(payload.datasetId()).thenReturn(id);
        when(payload.tenantId()).thenReturn("tenant-1");
        when(payload.dataset()).thenReturn(ds);
        return payload;
    }

    private ConsumerRecord<String, Object> record() {
        return new ConsumerRecord<>("catalog.datasets.changes", 0, 0L, "key", new Object());
    }
}
