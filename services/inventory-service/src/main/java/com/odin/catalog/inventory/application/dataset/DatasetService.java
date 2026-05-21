package com.odin.catalog.inventory.application.dataset;

import com.odin.catalog.inventory.api.v1.dto.DatasetRequest;
import com.odin.catalog.inventory.api.v1.dto.DatasetResponse;
import com.odin.catalog.inventory.api.v1.dto.PageResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final CatalogEventProducer eventProducer;

    @Transactional(readOnly = true)
    public PageResponse<DatasetResponse> list(UUID catalogId, String sourceUri, Pageable pageable) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        if (sourceUri != null && !sourceUri.isBlank()) {
            return datasetRepository.findBySourceUri(sourceUri).stream()
                .filter(e -> !e.isDeleted())
                .findFirst()
                .map(e -> PageResponse.ofSingle(toResponse(e)))
                .orElse(PageResponse.empty());
        }
        var page = catalogId != null
            ? datasetRepository.findByTenantIdAndCatalogIdAndIsDeletedFalse(tenantId, catalogId, pageable)
            : datasetRepository.findByTenantIdAndIsDeletedFalse(tenantId, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DatasetResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public DatasetResponse create(DatasetRequest request) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        DatasetEntity entity = new DatasetEntity();
        applyRequest(entity, request, tenantId);
        entity = datasetRepository.save(entity);
        eventProducer.publishDatasetChanged("CREATED", entity);
        return toResponse(entity);
    }

    @Transactional
    public DatasetResponse update(UUID id, DatasetRequest request) {
        DatasetEntity entity = findOrThrow(id);
        applyRequest(entity, request, entity.getTenantId());
        entity = datasetRepository.save(entity);
        eventProducer.publishDatasetChanged("UPDATED", entity);
        return toResponse(entity);
    }

    @Transactional
    public void delete(UUID id) {
        DatasetEntity entity = findOrThrow(id);
        entity.setDeleted(true);
        datasetRepository.save(entity);
        eventProducer.publishDatasetChanged("DELETED", entity);
    }

    private void applyRequest(DatasetEntity entity, DatasetRequest req, UUID tenantId) {
        entity.setTenantId(tenantId);
        entity.setTitle(req.title());
        entity.setDescription(req.description());
        entity.setCatalogId(req.catalogId());
        entity.setDomainId(req.domainId());
        entity.setAccrualPeriodicity(req.accrualPeriodicity());
        entity.setKeywords(req.keywords());
        entity.setThemes(req.themes());
        entity.setLanguage(req.language());
        entity.setLicense(req.license());
        entity.setVersion(req.version());
        entity.setSourceUri(req.sourceUri());
    }

    private DatasetEntity findOrThrow(UUID id) {
        return datasetRepository.findById(id)
            .filter(e -> !e.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + id));
    }

    DatasetResponse toResponse(DatasetEntity e) {
        return new DatasetResponse(
            e.getId(), e.getTitle(), e.getDescription(),
            e.getCatalogId(), e.getDomainId(), e.getTenantId(),
            e.getAccrualPeriodicity(), e.getKeywords(), e.getThemes(),
            e.getLanguage(), e.getLicense(), e.getVersion(),
            e.getSourceUri(), e.isDeleted(),
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
