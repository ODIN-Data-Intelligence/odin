package com.odin.catalog.ai.rag;

import com.odin.catalog.shared.kafka.consumer.KafkaEventConsumer;
import com.odin.catalog.shared.kafka.topics.CatalogTopics;
import com.odin.catalog.shared.models.events.DatasetChangedPayload;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final VectorStore vectorStore;
    private final KafkaEventConsumer kafkaEventConsumer;

    @KafkaListener(
        topics = CatalogTopics.DATASETS_CHANGES,
        groupId = "ai-consumer-catalog",
        concurrency = "2"
    )
    public void onDatasetChanged(ConsumerRecord<String, Object> record) {
        try {
            var envelope = kafkaEventConsumer.unwrap(record, DatasetChangedPayload.class);
            var payload = envelope.payload();

            if ("DELETED".equals(payload.changeType())) {
                List<String> ids = IntStream.range(0, 10)
                    .mapToObj(i -> chunkId(payload.datasetId(), i))
                    .collect(Collectors.toList());
                vectorStore.delete(ids);
                return;
            }

            if (payload.dataset() == null) return;

            List<Document> chunks = buildChunks(payload);
            vectorStore.add(chunks);
            log.debug("Embedded {} chunks for dataset {}", chunks.size(), payload.datasetId());
        } catch (Exception e) {
            log.error("Failed to embed dataset from offset {}: {}", record.offset(), e.getMessage(), e);
        }
    }

    private List<Document> buildChunks(DatasetChangedPayload payload) {
        List<Document> chunks = new ArrayList<>();
        var ds = payload.dataset();
        var resource = ds.resource();

        Map<String, Object> meta = Map.of("entityType", "DATASET", "entityId", payload.datasetId(), "tenantId", payload.tenantId());

        // Chunk 0: title + description
        String titleDesc = "Title: " + resource.title() +
            (resource.description() != null ? "\nDescription: " + resource.description() : "");
        chunks.add(new Document(chunkId(payload.datasetId(), 0), titleDesc, meta));

        // Chunk 1: keywords + themes
        if (resource.keywords() != null || resource.themes() != null) {
            String kwThemes = "Keywords: " + (resource.keywords() != null ? String.join(", ", resource.keywords()) : "") +
                "\nThemes: " + (resource.themes() != null ? String.join(", ", resource.themes()) : "");
            chunks.add(new Document(chunkId(payload.datasetId(), 1), kwThemes, meta));
        }

        return chunks;
    }

    private static String chunkId(String datasetId, int chunkIndex) {
        return UUID.nameUUIDFromBytes((datasetId + ":" + chunkIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
