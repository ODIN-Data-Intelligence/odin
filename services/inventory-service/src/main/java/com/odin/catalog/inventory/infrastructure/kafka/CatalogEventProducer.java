package com.odin.catalog.inventory.infrastructure.kafka;

import com.odin.catalog.inventory.api.v1.dto.DatasetSemanticContext;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DataProductEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.shared.kafka.producer.KafkaEventPublisher;
import com.odin.catalog.shared.kafka.topics.CatalogTopics;
import com.odin.catalog.shared.models.dcat.DcatDataset;
import com.odin.catalog.shared.models.dcat.DcatResource;
import com.odin.catalog.shared.models.events.DataProductChangedPayload;
import com.odin.catalog.shared.models.events.DatasetChangedPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogEventProducer {

    private static final Logger log = LoggerFactory.getLogger(CatalogEventProducer.class);

    private final KafkaEventPublisher publisher;

    public void publishDatasetChanged(String changeType, DatasetEntity entity) {
        publishDatasetChanged(changeType, entity, null);
    }

    public void publishDatasetChanged(String changeType, DatasetEntity entity, DatasetSemanticContext ctx) {
        DcatDataset dataset = buildDcatDataset(entity);
        DatasetChangedPayload payload = ctx != null
            ? new DatasetChangedPayload(
                changeType,
                entity.getId().toString(),
                entity.getCatalogId() != null ? entity.getCatalogId().toString() : null,
                entity.getDomainId() != null ? entity.getDomainId().toString() : null,
                entity.getTenantId().toString(),
                dataset,
                ctx.semanticTypes(),
                ctx.vocabConceptLabels(),
                ctx.vocabConceptIris(),
                ctx.fiboConcepts(),
                ctx.logicalElementNames(),
                ctx.logicalTypes())
            : DatasetChangedPayload.ofBasic(
                changeType,
                entity.getId().toString(),
                entity.getCatalogId() != null ? entity.getCatalogId().toString() : null,
                entity.getDomainId() != null ? entity.getDomainId().toString() : null,
                entity.getTenantId().toString(),
                dataset);
        publisher.publishAsync(
            CatalogTopics.DATASETS_CHANGES,
            entity.getId().toString(),
            "DatasetChanged",
            entity.getTenantId().toString(),
            payload
        );
        log.info("action=EVENT_PUBLISHED topic={} eventType=DatasetChanged entityId={} changeType={}",
            CatalogTopics.DATASETS_CHANGES, entity.getId(), changeType);
    }

    public void publishDataProductChanged(String changeType, DataProductEntity entity) {
        publishDataProductChanged(changeType, entity, null);
    }

    public void publishDataProductChanged(String changeType, DataProductEntity entity, String previousStatus) {
        var payload = new DataProductChangedPayload(
            changeType,
            entity.getId().toString(),
            entity.getDomainId() != null ? entity.getDomainId().toString() : null,
            entity.getTenantId().toString(),
            previousStatus,
            null
        );
        publisher.publishAsync(
            CatalogTopics.DATA_PRODUCTS_CHANGES,
            entity.getId().toString(),
            "DataProductChanged",
            entity.getTenantId().toString(),
            payload
        );
        log.info("action=EVENT_PUBLISHED topic={} eventType=DataProductChanged entityId={} changeType={}",
            CatalogTopics.DATA_PRODUCTS_CHANGES, entity.getId(), changeType);
    }

    private DcatDataset buildDcatDataset(DatasetEntity entity) {
        DcatResource resource = new DcatResource(
            entity.getId().toString(),
            entity.getResourceType(),
            entity.getIri(),
            entity.getTenantId().toString(),
            entity.getDomainId() != null ? entity.getDomainId().toString() : null,
            entity.getTitle(),
            entity.getDescription(),
            entity.getLanguage(),
            entity.getKeywords(),
            entity.getThemes(),
            entity.getIssued() != null ? entity.getIssued().toString() : null,
            entity.getModified() != null ? entity.getModified().toString() : null,
            entity.getLicense(),
            entity.getRightsStatement(),
            entity.getAccessRights(),
            entity.getConformsTo(),
            entity.getCreatorId() != null ? entity.getCreatorId().toString() : null,
            entity.getPublisherId() != null ? entity.getPublisherId().toString() : null,
            null,
            entity.getSourceUri(),
            null
        );
        return new DcatDataset(
            resource,
            entity.getAccrualPeriodicity(),
            null,
            entity.getSpatialResolutionM() != null ? entity.getSpatialResolutionM().toString() : null,
            entity.getTemporalResolution(),
            entity.getVersion(),
            entity.getVersionNotes(),
            entity.getIsVersionOf() != null ? entity.getIsVersionOf().getId().toString() : null,
            null,
            null
        );
    }
}
