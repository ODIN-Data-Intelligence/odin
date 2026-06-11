package com.odin.catalog.inventory.application.distribution;

import com.odin.catalog.inventory.api.v1.dto.CsvwColumnRequest;
import com.odin.catalog.inventory.api.v1.dto.CsvwColumnResponse;
import com.odin.catalog.inventory.api.v1.dto.DistributionRequest;
import com.odin.catalog.inventory.api.v1.dto.DistributionResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.CsvwColumnEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DistributionEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.CsvwColumnRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DistributionRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DistributionService {

    private static final Logger log = LoggerFactory.getLogger(DistributionService.class);

    private final DistributionRepository distributionRepository;
    private final CsvwColumnRepository csvwColumnRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public Page<DistributionResponse> listAll(Pageable pageable) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        return distributionRepository.findByTenantIdAndIsDeletedFalse(tenantId, pageable)
            .map(DistributionResponse::from);
    }

    @Transactional(readOnly = true)
    public List<DistributionResponse> listByDataset(UUID datasetId) {
        return distributionRepository.findByDatasetIdAndIsDeletedFalse(datasetId)
            .stream().map(DistributionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DistributionResponse get(UUID id) {
        return DistributionResponse.from(findOrThrow(id));
    }

    @Transactional
    public DistributionResponse create(UUID datasetId, DistributionRequest request) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        DistributionEntity dist = new DistributionEntity();
        dist.setTenantId(tenantId);
        dist.setDatasetId(datasetId);
        applyRequest(dist, request);
        DistributionEntity saved = distributionRepository.save(dist);
        log.info("action=CREATE_DISTRIBUTION distributionId={} datasetId={}", saved.getId(), datasetId);
        return DistributionResponse.from(saved);
    }

    @Transactional
    public DistributionResponse update(UUID id, DistributionRequest request) {
        DistributionEntity dist = findOrThrow(id);
        applyRequest(dist, request);
        DistributionEntity saved = distributionRepository.save(dist);
        log.info("action=UPDATE_DISTRIBUTION distributionId={} datasetId={}", saved.getId(), saved.getDatasetId());
        return DistributionResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        DistributionEntity dist = findOrThrow(id);
        dist.setDeleted(true);
        distributionRepository.save(dist);
        log.info("action=DELETE_DISTRIBUTION distributionId={} datasetId={}", id, dist.getDatasetId());
    }

    @Transactional(readOnly = true)
    public List<CsvwColumnResponse> getPhysicalSchema(UUID ownerId) {
        UUID schemaId = schemaId(ownerId);
        return csvwColumnRepository.findBySchemaIdOrderByOrdinalAsc(schemaId)
            .stream().map(CsvwColumnResponse::from).toList();
    }

    @Transactional
    public List<CsvwColumnResponse> setPhysicalSchema(UUID ownerId, List<CsvwColumnRequest> columns) {
        log.info("action=SET_PHYSICAL_SCHEMA ownerId={} columnCount={}", ownerId, columns.size());
        return upsertSchema(ownerId, columns);
    }

    // --- helpers ---

    private DistributionEntity findOrThrow(UUID id) {
        return distributionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Distribution not found: " + id));
    }

    private void applyRequest(DistributionEntity e, DistributionRequest req) {
        e.setTitle(req.title());
        e.setDescription(req.description());
        e.setAccessUrl(req.accessUrl());
        e.setDownloadUrl(req.downloadUrl());
        e.setMediaType(req.mediaType());
        e.setFormat(req.format());
        e.setByteSize(req.byteSize());
        e.setChecksumAlgorithm(req.checksumAlgorithm());
        e.setChecksumValue(req.checksumValue());
        e.setCompressFormat(req.compressFormat());
        e.setPackageFormat(req.packageFormat());
        e.setAvailability(req.availability());
        e.setDatabaseName(req.databaseName());
        e.setSchemaName(req.schemaName());
        e.setTableName(req.tableName());
    }

    private List<CsvwColumnResponse> upsertSchema(UUID ownerId, List<CsvwColumnRequest> columns) {
        UUID tableId  = UUID.nameUUIDFromBytes((ownerId.toString() + ":table").getBytes());
        UUID schemaId = schemaId(ownerId);

        jdbcTemplate.update("""
            INSERT INTO csvw_tables (id, title)
            VALUES (?::uuid, ?)
            ON CONFLICT (id) DO NOTHING
            """, tableId.toString(), "Physical Schema");

        jdbcTemplate.update("""
            INSERT INTO csvw_table_schemas (id, table_id)
            VALUES (?::uuid, ?::uuid)
            ON CONFLICT (id) DO NOTHING
            """, schemaId.toString(), tableId.toString());

        csvwColumnRepository.deleteBySchemaId(schemaId);

        List<CsvwColumnResponse> saved = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            CsvwColumnRequest col = columns.get(i);
            CsvwColumnEntity entity = new CsvwColumnEntity();
            entity.setSchemaId(schemaId);
            entity.setOrdinal(i + 1);
            entity.setName(col.name());
            entity.setDatatype(col.datatype());
            entity.setDescription(col.description());
            entity.setRequired(col.required());
            entity.setTitles(col.titles());
            entity.setPropertyUrl(col.propertyUrl());
            entity.setVirtual(col.virtual());
            entity.setSuppressOutput(col.suppressOutput());
            entity.setLang(col.lang());
            entity.setDefaultValue(col.defaultValue());
            entity.setValueUrl(col.valueUrl());
            entity.setAboutUrl(col.aboutUrl());
            saved.add(CsvwColumnResponse.from(csvwColumnRepository.save(entity)));
        }
        return saved;
    }

    private static UUID schemaId(UUID ownerId) {
        return UUID.nameUUIDFromBytes((ownerId.toString() + ":schema").getBytes());
    }
}
