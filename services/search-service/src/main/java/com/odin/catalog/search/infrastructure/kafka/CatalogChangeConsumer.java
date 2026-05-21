package com.odin.catalog.search.infrastructure.kafka;

import com.odin.catalog.search.infrastructure.opensearch.CatalogSearchDocument;
import com.odin.catalog.search.infrastructure.opensearch.OpenSearchIndexService;
import com.odin.catalog.shared.kafka.consumer.KafkaEventConsumer;
import com.odin.catalog.shared.kafka.topics.CatalogTopics;
import com.odin.catalog.shared.models.events.DatasetChangedPayload;
import com.odin.catalog.shared.models.events.DataProductChangedPayload;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CatalogChangeConsumer {

    private static final Logger log = LoggerFactory.getLogger(CatalogChangeConsumer.class);

    private final KafkaEventConsumer kafkaEventConsumer;
    private final OpenSearchIndexService indexService;

    @KafkaListener(
        topics = CatalogTopics.DATASETS_CHANGES,
        groupId = "search-consumer-catalog",
        concurrency = "3"
    )
    public void onDatasetChanged(ConsumerRecord<String, Object> record) {
        try {
            var envelope = kafkaEventConsumer.unwrap(record, DatasetChangedPayload.class);
            DatasetChangedPayload payload = envelope.payload();

            if ("DELETED".equals(payload.changeType())) {
                indexService.delete(payload.datasetId());
                return;
            }

            // Build a search document from the payload
            CatalogSearchDocument doc = buildDatasetDocument(payload);
            indexService.index(doc);
        } catch (Exception e) {
            log.error("Failed to index dataset change from offset {}: {}", record.offset(), e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = CatalogTopics.DATA_PRODUCTS_CHANGES,
        groupId = "search-consumer-catalog",
        concurrency = "2"
    )
    public void onDataProductChanged(ConsumerRecord<String, Object> record) {
        try {
            var envelope = kafkaEventConsumer.unwrap(record, DataProductChangedPayload.class);
            DataProductChangedPayload payload = envelope.payload();

            if ("DELETED".equals(payload.changeType())) {
                indexService.delete(payload.dataProductId());
                return;
            }

            CatalogSearchDocument doc = buildDataProductDocument(payload);
            indexService.index(doc);
        } catch (Exception e) {
            log.error("Failed to index data product change from offset {}: {}", record.offset(), e.getMessage(), e);
        }
    }

    private CatalogSearchDocument buildDatasetDocument(DatasetChangedPayload payload) {
        var ds = payload.dataset();
        return new CatalogSearchDocument(
            payload.datasetId(), payload.tenantId(), "DATASET",
            ds != null ? ds.resource().title() : null,
            ds != null ? ds.resource().description() : null,
            ds != null ? ds.resource().keywords() : List.of(),
            ds != null ? ds.resource().themes() : List.of(),
            payload.domainId(), null, null, null, null, null,
            ds != null ? ds.resource().license() : null,
            null, null, ds != null ? ds.accrualPeriodicity() : null,
            ds != null ? ds.resource().sourceUri() : null,
            null, null, false, false, false, 0,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of()
        );
    }

    private CatalogSearchDocument buildDataProductDocument(DataProductChangedPayload payload) {
        var dp = payload.dataProduct();
        return new CatalogSearchDocument(
            payload.dataProductId(), payload.tenantId(), "DATA_PRODUCT",
            dp != null ? dp.resource().title() : null,
            dp != null ? dp.resource().description() : null,
            dp != null ? dp.resource().keywords() : List.of(),
            dp != null ? dp.resource().themes() : List.of(),
            payload.domainId(), null, null, null,
            dp != null ? dp.lifecycleStatus().name() : null,
            null, dp != null ? dp.resource().license() : null,
            null, null, null, null, null, null,
            false, false, false, 0,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of()
        );
    }
}
