package com.odin.catalog.harvest.application;

import com.odin.catalog.harvest.api.v1.dto.HarvestSourceRequest;
import com.odin.catalog.harvest.api.v1.dto.HarvestSourceResponse;
import com.odin.catalog.harvest.connector.HarvestConnector;
import com.odin.catalog.harvest.domain.source.HarvestSource;
import com.odin.catalog.harvest.domain.source.SourceCredentials;
import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestSourceEntity;
import com.odin.catalog.harvest.infrastructure.jpa.repository.HarvestSourceRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HarvestSourceService {

    private static final Logger log = LoggerFactory.getLogger(HarvestSourceService.class);

    private final HarvestSourceRepository sourceRepository;
    private final List<HarvestConnector> connectors;

    private Map<String, HarvestConnector> connectorMap() {
        return connectors.stream().collect(
            Collectors.toMap(HarvestConnector::sourceType, Function.identity())
        );
    }

    @Transactional(readOnly = true)
    public List<HarvestSourceResponse> list(String type) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        var sources = type != null
            ? sourceRepository.findByTenantIdAndSourceType(tenantId, type)
            : sourceRepository.findByTenantId(tenantId);
        return sources.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public HarvestSourceResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public HarvestSourceResponse create(HarvestSourceRequest request) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        HarvestSourceEntity entity = new HarvestSourceEntity();
        entity.setTenantId(tenantId);
        applyRequest(entity, request);
        HarvestSourceResponse result = toResponse(sourceRepository.save(entity));
        log.info("action=HARVEST_SOURCE_CREATED sourceId={} tenantId={} name={} type={}",
            result.id(), tenantId, request.name(), request.sourceType());
        return result;
    }

    @Transactional
    public HarvestSourceResponse update(UUID id, HarvestSourceRequest request) {
        HarvestSourceEntity entity = findOrThrow(id);
        applyRequest(entity, request);
        HarvestSourceResponse result = toResponse(sourceRepository.save(entity));
        log.info("action=HARVEST_SOURCE_UPDATED sourceId={} name={}", id, request.name());
        return result;
    }

    @Transactional
    public void delete(UUID id) {
        sourceRepository.deleteById(id);
        log.info("action=HARVEST_SOURCE_DELETED sourceId={}", id);
    }

    public boolean testConnection(UUID id) {
        HarvestSourceEntity entity = findOrThrow(id);
        HarvestConnector connector = connectorMap().get(entity.getSourceType());
        if (connector == null) {
            log.warn("action=HARVEST_CONNECTION_TEST_FAILED sourceId={} reason=no_connector_for_type type={}", id, entity.getSourceType());
            return false;
        }
        HarvestSource source = toSource(entity);
        boolean ok = connector.testConnection(source);
        log.info("action=HARVEST_CONNECTION_TEST sourceId={} type={} result={}", id, entity.getSourceType(), ok ? "SUCCESS" : "FAILED");
        return ok;
    }

    private void applyRequest(HarvestSourceEntity entity, HarvestSourceRequest req) {
        entity.setName(req.name());
        entity.setSourceType(req.sourceType());
        entity.setBaseUrl(req.baseUrl());
        entity.setRegion(req.region());
        entity.setDatabaseName(req.databaseName());
        entity.setSchemaFilter(req.schemaFilter());
        entity.setCredentialRef(req.credentialRef());
    }

    HarvestSourceEntity findOrThrow(UUID id) {
        return sourceRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("HarvestSource not found: " + id));
    }

    HarvestSource toSource(HarvestSourceEntity entity) {
        return new HarvestSource(
            entity.getId(), entity.getTenantId(), entity.getName(),
            entity.getSourceType(), entity.getBaseUrl(), entity.getRegion(),
            entity.getDatabaseName(), entity.getSchemaFilter(), entity.getCredentialRef(),
            new SourceCredentials(null, null, null, null, null, null, null, null, null),
            null
        );
    }

    HarvestSourceResponse toResponse(HarvestSourceEntity e) {
        return new HarvestSourceResponse(
            e.getId(), e.getTenantId(), e.getName(), e.getSourceType(),
            e.getBaseUrl(), e.getRegion(), e.getDatabaseName(),
            e.getSchemaFilter(), e.getCreatedAt()
        );
    }
}
