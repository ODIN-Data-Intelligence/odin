package com.odin.catalog.ai.rag;

import com.odin.catalog.ai.client.CatalogServiceClient;
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
import java.util.Comparator;
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
    private final CatalogServiceClient catalogClient;

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
                log.info("action=EMBEDDINGS_DELETED datasetId={}", payload.datasetId());
                return;
            }

            if (payload.dataset() == null) return;

            List<Document> chunks = buildChunks(payload);
            vectorStore.add(chunks);
            log.info("action=EMBEDDINGS_UPSERTED datasetId={} chunkCount={} changeType={}",
                payload.datasetId(), chunks.size(), payload.changeType());
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

        // Chunk 2: semantic graph context from vocabulary mappings
        List<String> types  = payload.semanticTypes()    != null ? payload.semanticTypes()    : List.of();
        List<String> labels = payload.vocabConceptLabels() != null ? payload.vocabConceptLabels() : List.of();
        List<String> names  = payload.logicalElementNames() != null ? payload.logicalElementNames() : List.of();
        if (!types.isEmpty() || !labels.isEmpty()) {
            String typeChunk =
                "Semantic types: "      + String.join(", ", types)  + "\n" +
                "Vocabulary concepts: " + String.join(", ", labels) + "\n" +
                "Business elements: "   + String.join(", ", names);
            chunks.add(new Document(chunkId(payload.datasetId(), 2), typeChunk, meta));
        }

        // Chunk 3: physical column schema — enables RAG to surface column names for multi-dataset queries
        try {
            List<CatalogServiceClient.PhysicalColumn> columns = catalogClient.getDatasetPhysicalSchema(payload.datasetId());
            if (!columns.isEmpty()) {
                String tableName = resource.title() != null ? resource.title().replaceAll("[^a-zA-Z0-9_]", "_") : payload.datasetId();
                String schemaChunk = "Physical schema (table: " + tableName + "):\n" +
                    columns.stream()
                        .sorted(Comparator.comparingInt(c -> c.ordinal() != null ? c.ordinal() : 0))
                        .map(c -> "  " + c.name() +
                            (c.datatype() != null ? " [" + c.datatype() + "]" : "") +
                            (Boolean.TRUE.equals(c.required()) ? " NOT NULL" : ""))
                        .collect(Collectors.joining("\n"));
                chunks.add(new Document(chunkId(payload.datasetId(), 3), schemaChunk, meta));
            }
        } catch (Exception e) {
            log.debug("Could not embed physical schema for dataset {}: {}", payload.datasetId(), e.getMessage());
        }

        return chunks;
    }

    private static String chunkId(String datasetId, int chunkIndex) {
        return UUID.nameUUIDFromBytes((datasetId + ":" + chunkIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
