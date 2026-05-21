package com.odin.catalog.search.infrastructure.kafka;

import com.odin.catalog.search.infrastructure.opensearch.CatalogSearchDocument;
import com.odin.catalog.search.infrastructure.opensearch.OpenSearchIndexService;
import com.odin.catalog.shared.kafka.consumer.KafkaEventConsumer;
import com.odin.catalog.shared.models.events.DatasetChangedPayload;
import com.odin.catalog.shared.models.events.DataProductChangedPayload;
import com.odin.catalog.shared.models.events.KafkaEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogChangeConsumerTest {

    @Mock KafkaEventConsumer kafkaEventConsumer;
    @Mock OpenSearchIndexService indexService;

    @InjectMocks CatalogChangeConsumer consumer;

    // ── dataset changed ───────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void onDatasetChanged_upsertEvent_indexesDocument() throws Exception {
        DatasetChangedPayload payload = mock(DatasetChangedPayload.class);
        when(payload.changeType()).thenReturn("UPDATED");
        when(payload.datasetId()).thenReturn("ds-123");
        when(payload.tenantId()).thenReturn("tenant-1");
        when(payload.dataset()).thenReturn(null);

        KafkaEnvelope<DatasetChangedPayload> envelope = mock(KafkaEnvelope.class);
        when(envelope.payload()).thenReturn(payload);
        when(kafkaEventConsumer.unwrap(any(), eq(DatasetChangedPayload.class))).thenReturn(envelope);

        consumer.onDatasetChanged(record());

        ArgumentCaptor<CatalogSearchDocument> captor = ArgumentCaptor.forClass(CatalogSearchDocument.class);
        verify(indexService).index(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo("ds-123");
        assertThat(captor.getValue().entityType()).isEqualTo("DATASET");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onDatasetChanged_deletedEvent_deletesFromIndex() throws Exception {
        DatasetChangedPayload payload = mock(DatasetChangedPayload.class);
        when(payload.changeType()).thenReturn("DELETED");
        when(payload.datasetId()).thenReturn("ds-456");

        KafkaEnvelope<DatasetChangedPayload> envelope = mock(KafkaEnvelope.class);
        when(envelope.payload()).thenReturn(payload);
        when(kafkaEventConsumer.unwrap(any(), eq(DatasetChangedPayload.class))).thenReturn(envelope);

        consumer.onDatasetChanged(record());

        verify(indexService).delete("ds-456");
        verify(indexService, never()).index(any());
    }

    @Test
    void onDatasetChanged_deserializationFailure_doesNotThrow() throws Exception {
        when(kafkaEventConsumer.unwrap(any(), eq(DatasetChangedPayload.class)))
            .thenThrow(new RuntimeException("bad message"));

        consumer.onDatasetChanged(record());

        verifyNoInteractions(indexService);
    }

    // ── data product changed ──────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void onDataProductChanged_upsertEvent_indexesDocument() throws Exception {
        DataProductChangedPayload payload = mock(DataProductChangedPayload.class);
        when(payload.changeType()).thenReturn("CREATED");
        when(payload.dataProductId()).thenReturn("dp-789");
        when(payload.tenantId()).thenReturn("tenant-1");
        when(payload.dataProduct()).thenReturn(null);

        KafkaEnvelope<DataProductChangedPayload> envelope = mock(KafkaEnvelope.class);
        when(envelope.payload()).thenReturn(payload);
        when(kafkaEventConsumer.unwrap(any(), eq(DataProductChangedPayload.class))).thenReturn(envelope);

        consumer.onDataProductChanged(record());

        ArgumentCaptor<CatalogSearchDocument> captor = ArgumentCaptor.forClass(CatalogSearchDocument.class);
        verify(indexService).index(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo("dp-789");
        assertThat(captor.getValue().entityType()).isEqualTo("DATA_PRODUCT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onDataProductChanged_deletedEvent_deletesFromIndex() throws Exception {
        DataProductChangedPayload payload = mock(DataProductChangedPayload.class);
        when(payload.changeType()).thenReturn("DELETED");
        when(payload.dataProductId()).thenReturn("dp-999");

        KafkaEnvelope<DataProductChangedPayload> envelope = mock(KafkaEnvelope.class);
        when(envelope.payload()).thenReturn(payload);
        when(kafkaEventConsumer.unwrap(any(), eq(DataProductChangedPayload.class))).thenReturn(envelope);

        consumer.onDataProductChanged(record());

        verify(indexService).delete("dp-999");
        verify(indexService, never()).index(any());
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private ConsumerRecord<String, Object> record() {
        return new ConsumerRecord<>("catalog.datasets.changes", 0, 0L, "key", new Object());
    }
}
