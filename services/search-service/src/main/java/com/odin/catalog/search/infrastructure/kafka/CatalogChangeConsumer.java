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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CatalogChangeConsumer {

    private static final Logger log = LoggerFactory.getLogger(CatalogChangeConsumer.class);

    private final KafkaEventConsumer kafkaEventConsumer;
    private final OpenSearchIndexService indexService;

    @Value("${inventory.service.url:http://inventory-service:8001}")
    private String inventoryServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @KafkaListener(
        topics = CatalogTopics.DATASETS_CHANGES,
        groupId = "search-consumer-catalog",
        concurrency = "3"
    )
    public void onDatasetChanged(ConsumerRecord<String, Object> record) {
        log.debug("action=EVENT_RECEIVED topic={} offset={} key={}", record.topic(), record.offset(), record.key());
        long t = System.currentTimeMillis();
        try {
            var envelope = kafkaEventConsumer.unwrap(record, DatasetChangedPayload.class);
            DatasetChangedPayload payload = envelope.payload();

            if ("DELETED".equals(payload.changeType())) {
                indexService.delete(payload.datasetId());
                deleteDistributionsForDataset(payload.datasetId(), payload.tenantId());
                log.info("action=EVENT_PROCESSED topic={} changeType=DELETED entityId={} elapsed={}ms",
                    record.topic(), payload.datasetId(), System.currentTimeMillis() - t);
                return;
            }

            CatalogSearchDocument doc = buildDatasetDocument(payload);
            indexService.index(doc);
            syncDistributionsForDataset(payload.datasetId(), payload.tenantId());
            log.info("action=EVENT_PROCESSED topic={} changeType={} entityId={} elapsed={}ms",
                record.topic(), payload.changeType(), payload.datasetId(), System.currentTimeMillis() - t);
        } catch (Exception e) {
            log.error("action=EVENT_PROCESSING_FAILED topic={} offset={} error={}",
                record.topic(), record.offset(), e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = CatalogTopics.DATA_PRODUCTS_CHANGES,
        groupId = "search-consumer-catalog",
        concurrency = "2"
    )
    public void onDataProductChanged(ConsumerRecord<String, Object> record) {
        log.debug("action=EVENT_RECEIVED topic={} offset={} key={}", record.topic(), record.offset(), record.key());
        long t = System.currentTimeMillis();
        try {
            var envelope = kafkaEventConsumer.unwrap(record, DataProductChangedPayload.class);
            DataProductChangedPayload payload = envelope.payload();

            if ("DELETED".equals(payload.changeType())) {
                indexService.delete(payload.dataProductId());
                log.info("action=EVENT_PROCESSED topic={} changeType=DELETED entityId={} elapsed={}ms",
                    record.topic(), payload.dataProductId(), System.currentTimeMillis() - t);
                return;
            }

            CatalogSearchDocument doc = buildDataProductDocument(payload);
            indexService.index(doc);
            log.info("action=EVENT_PROCESSED topic={} changeType={} entityId={} elapsed={}ms",
                record.topic(), payload.changeType(), payload.dataProductId(), System.currentTimeMillis() - t);
        } catch (Exception e) {
            log.error("action=EVENT_PROCESSING_FAILED topic={} offset={} error={}",
                record.topic(), record.offset(), e.getMessage(), e);
        }
    }

    private CatalogSearchDocument buildDatasetDocument(DatasetChangedPayload payload) {
        var ds = payload.dataset();
        List<String> elemNames = orEmpty(payload.logicalElementNames());
        boolean hasLogicalModel = !elemNames.isEmpty();
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
            null, null, false, false, hasLogicalModel, 0,
            elemNames,
            List.of(),
            orEmpty(payload.logicalTypes()),
            orEmpty(payload.vocabConceptIris()),
            orEmpty(payload.vocabConceptLabels()),
            List.of(),
            orEmpty(payload.fiboConcepts()),
            orEmpty(payload.semanticTypes()),
            List.of(), List.of(), null
        );
    }

    private static List<String> orEmpty(List<String> lst) {
        return lst != null ? lst : List.of();
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
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), null
        );
    }

    @SuppressWarnings("unchecked")
    private void syncDistributionsForDataset(String datasetId, String tenantId) {
        try {
            HttpHeaders hdrs = new HttpHeaders();
            hdrs.set("X-API-Key", "dev-reindex");
            hdrs.set("X-Tenant-Id", tenantId);
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                inventoryServiceUrl + "/api/v1/datasets/" + datasetId + "/distributions",
                HttpMethod.GET, new HttpEntity<>(hdrs),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> distributions = resp.getBody();
            if (distributions != null) {
                for (Map<String, Object> dist : distributions) {
                    indexService.index(buildDistributionDocument(dist, tenantId));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to sync distributions for dataset {}: {}", datasetId, e.getMessage());
        }
    }

    private void deleteDistributionsForDataset(String datasetId, String tenantId) {
        try {
            syncDistributionsForDataset(datasetId, tenantId);
        } catch (Exception ignored) {
            // best-effort: if we can't fetch, we can't cascade-delete
        }
    }

    @SuppressWarnings("unchecked")
    private CatalogSearchDocument buildDistributionDocument(Map<String, Object> dist, String tenantId) {
        List<String> keywords = (List<String>) dist.getOrDefault("keywords", List.of());
        List<String> themes = (List<String>) dist.getOrDefault("themes", List.of());
        return new CatalogSearchDocument(
            (String) dist.get("id"), tenantId, "DISTRIBUTION",
            (String) dist.get("title"),
            (String) dist.get("description"),
            keywords != null ? keywords : List.of(),
            themes != null ? themes : List.of(),
            (String) dist.get("domainId"), null, null, null, null, null,
            (String) dist.get("license"),
            (String) dist.get("format"),
            (String) dist.get("mediaType"),
            null, null, null, null, false, false, false, 0,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), (String) dist.get("datasetId")
        );
    }
}
