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
        return toResponse(sourceRepository.save(entity));
    }

    @Transactional
    public HarvestSourceResponse update(UUID id, HarvestSourceRequest request) {
        HarvestSourceEntity entity = findOrThrow(id);
        applyRequest(entity, request);
        return toResponse(sourceRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        sourceRepository.deleteById(id);
    }

    public boolean testConnection(UUID id) {
        HarvestSourceEntity entity = findOrThrow(id);
        HarvestConnector connector = connectorMap().get(entity.getSourceType());
        if (connector == null) return false;
        HarvestSource source = toSource(entity);
        return connector.testConnection(source);
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

    private HarvestSourceEntity findOrThrow(UUID id) {
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
