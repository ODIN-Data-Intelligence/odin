package com.odin.catalog.inventory.application.harvest;

import com.odin.catalog.inventory.application.logical.LogicalModelService;
import com.odin.catalog.inventory.infrastructure.jpa.entity.*;
import com.odin.catalog.inventory.infrastructure.jpa.repository.*;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import com.odin.catalog.shared.models.common.NormalizedColumn;
import com.odin.catalog.shared.models.events.HarvestDistributionPayload;
import com.odin.catalog.shared.models.events.HarvestEntityDiscoveredPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HarvestEntityProcessor {

    private static final Logger log = LoggerFactory.getLogger(HarvestEntityProcessor.class);

    private final DatasetRepository datasetRepository;
    private final DistributionRepository distributionRepository;
    private final LogicalModelRepository logicalModelRepository;
    private final LogicalDataElementRepository elementRepository;
    private final CsvwColumnRepository columnRepository;
    private final CatalogEventProducer eventProducer;
    private final LogicalModelService logicalModelService;

    @Transactional
    public void process(HarvestEntityDiscoveredPayload payload) {
        if (!"DATASET".equals(payload.entityType())) return;

        DatasetEntity dataset = upsertDataset(payload);
        UUID datasetId = dataset.getId();

        if (payload.distributions() != null && !payload.distributions().isEmpty()) {
            upsertDistributions(dataset, payload.distributions());
        }

        if (payload.columns() != null && !payload.columns().isEmpty()) {
            UUID schemaId = upsertDistributionAndSchema(dataset, payload);
            List<CsvwColumnEntity> columns = upsertColumns(schemaId, payload.columns());
            tryAutoScaffoldLogicalModel(datasetId, columns);
        }

        eventProducer.publishDatasetChanged("UPDATED", dataset);
        log.debug("Processed harvest entity: {} → dataset {}", payload.sourceUri(), datasetId);
    }

    private DatasetEntity upsertDataset(HarvestEntityDiscoveredPayload payload) {
        return datasetRepository.findBySourceUri(payload.sourceUri()).stream().findFirst()
            .map(existing -> {
                existing.setTitle(payload.title());
                existing.setDescription(payload.description());
                existing.setKeywords(payload.keywords());
                existing.setThemes(payload.themes());
                return datasetRepository.save(existing);
            })
            .orElseGet(() -> {
                DatasetEntity entity = new DatasetEntity();
                entity.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001")); // from Kafka envelope
                entity.setTitle(payload.title() != null ? payload.title() : payload.sourceKey());
                entity.setDescription(payload.description());
                entity.setKeywords(payload.keywords());
                entity.setThemes(payload.themes());
                entity.setSourceUri(payload.sourceUri());
                return datasetRepository.save(entity);
            });
    }

    private void upsertDistributions(DatasetEntity dataset, List<HarvestDistributionPayload> distributions) {
        for (HarvestDistributionPayload d : distributions) {
            String lookupUrl = d.downloadUrl() != null ? d.downloadUrl() : d.accessUrl();
            DistributionEntity entity = d.downloadUrl() != null
                ? distributionRepository.findByDatasetIdAndDownloadUrlAndIsDeletedFalse(dataset.getId(), d.downloadUrl()).orElse(null)
                : distributionRepository.findByDatasetIdAndAccessUrlAndIsDeletedFalse(dataset.getId(), d.accessUrl()).orElse(null);

            if (entity == null) {
                entity = new DistributionEntity();
                entity.setTenantId(dataset.getTenantId());
                entity.setDatasetId(dataset.getId());
            }
            entity.setTitle(d.title() != null ? d.title() : lookupUrl);
            entity.setDownloadUrl(d.downloadUrl());
            entity.setAccessUrl(d.accessUrl());
            entity.setFormat(d.format());
            entity.setMediaType(d.mediaType());
            distributionRepository.save(entity);
        }
    }

    private UUID upsertDistributionAndSchema(DatasetEntity dataset, HarvestEntityDiscoveredPayload payload) {
        // Returns a deterministic schema UUID derived from the dataset ID.
        // A full implementation would look up or create the csvw_tables → csvw_table_schemas chain.
        return UUID.nameUUIDFromBytes((dataset.getId().toString() + ":schema").getBytes());
    }

    private List<CsvwColumnEntity> upsertColumns(UUID schemaId, List<NormalizedColumn> normalizedColumns) {
        return normalizedColumns.stream().map(nc -> {
            return columnRepository.findBySchemaIdAndNameIgnoreCase(schemaId, nc.name())
                .map(existing -> {
                    existing.setDatatype(nc.datatype());
                    existing.setDescription(nc.description());
                    return columnRepository.save(existing);
                })
                .orElseGet(() -> {
                    CsvwColumnEntity col = new CsvwColumnEntity();
                    col.setSchemaId(schemaId);
                    col.setOrdinal(nc.ordinal());
                    col.setName(nc.name());
                    col.setDatatype(nc.datatype());
                    col.setRequired(nc.required());
                    col.setDescription(nc.description());
                    return columnRepository.save(col);
                });
        }).toList();
    }

    private void tryAutoScaffoldLogicalModel(UUID datasetId, List<CsvwColumnEntity> columns) {
        boolean alreadyHasModel = !logicalModelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId).isEmpty();
        if (alreadyHasModel) {
            // Try to auto-bind unbound elements by name
            logicalModelRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId).stream()
                .flatMap(m -> elementRepository.findByLogicalModelIdOrderByOrdinalAsc(m.getId()).stream())
                .forEach(element -> columns.stream()
                    .filter(col -> col.getName().equalsIgnoreCase(element.getName())
                                && col.getLogicalDataElementId() == null)
                    .findFirst()
                    .ifPresent(col -> {
                        col.setLogicalDataElementId(element.getId());
                        columnRepository.save(col);
                    }));
            return;
        }

        // Auto-scaffold a draft LogicalModel from physical columns
        LogicalModelEntity model = new LogicalModelEntity();
        model.setDatasetId(datasetId);
        model.setName("Auto-generated from harvest");
        model.setStatus("draft");
        model = logicalModelRepository.save(model);

        final UUID modelId = model.getId();
        for (int i = 0; i < columns.size(); i++) {
            CsvwColumnEntity col = columns.get(i);
            LogicalDataElementEntity element = new LogicalDataElementEntity();
            element.setLogicalModelId(modelId);
            element.setName(col.getName());
            element.setOrdinal(i);
            element.setLogicalType(inferLogicalType(col.getDatatype()));
            elementRepository.save(element);
            col.setLogicalDataElementId(element.getId());
            columnRepository.save(col);
        }

        datasetRepository.findById(datasetId).ifPresent(ds ->
            logicalModelService.auditModelAutoScaffold(modelId, datasetId, ds.getTenantId(), columns.size()));
    }

    private String inferLogicalType(String sqlType) {
        if (sqlType == null) return null;
        String t = sqlType.toUpperCase();
        if (t.contains("DATE") || t.contains("TIME")) return "Date";
        if (t.contains("DECIMAL") || t.contains("NUMERIC") || t.contains("FLOAT") || t.contains("DOUBLE")) return "Measure";
        if (t.contains("INT") || t.contains("BIGINT")) return "Count";
        if (t.contains("BOOL")) return "Flag";
        return "Text";
    }
}
