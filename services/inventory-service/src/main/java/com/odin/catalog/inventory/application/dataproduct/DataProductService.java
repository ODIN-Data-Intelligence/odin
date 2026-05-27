package com.odin.catalog.inventory.application.dataproduct;

import com.odin.catalog.inventory.api.v1.dto.DataProductRequest;
import com.odin.catalog.inventory.api.v1.dto.DataProductResponse;
import com.odin.catalog.inventory.api.v1.dto.DatasetResponse;
import com.odin.catalog.inventory.api.v1.dto.PageResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DataProductEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DataProductPortEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DataProductPortRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DataProductRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataProductService {

    private final DataProductRepository dataProductRepository;
    private final DataProductPortRepository portRepository;
    private final DatasetRepository datasetRepository;
    private final CatalogEventProducer eventProducer;

    @Transactional(readOnly = true)
    public PageResponse<DataProductResponse> list(UUID domainId, String lifecycleStatus, Pageable pageable) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        var page = domainId != null
            ? dataProductRepository.findByTenantIdAndDomainIdAndIsDeletedFalse(tenantId, domainId, pageable)
            : lifecycleStatus != null
                ? dataProductRepository.findByTenantIdAndLifecycleStatusAndIsDeletedFalse(tenantId, lifecycleStatus, pageable)
                : dataProductRepository.findByTenantIdAndIsDeletedFalse(tenantId, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DataProductResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public DataProductResponse create(DataProductRequest request) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        DataProductEntity entity = new DataProductEntity();
        applyRequest(entity, request, tenantId);
        entity = dataProductRepository.save(entity);
        eventProducer.publishDataProductChanged("CREATED", entity);
        return toResponse(entity);
    }

    @Transactional
    public DataProductResponse update(UUID id, DataProductRequest request) {
        DataProductEntity entity = findOrThrow(id);
        applyRequest(entity, request, entity.getTenantId());
        entity = dataProductRepository.save(entity);
        eventProducer.publishDataProductChanged("UPDATED", entity);
        return toResponse(entity);
    }

    @Transactional
    public DataProductResponse transitionLifecycle(UUID id, String newStatus) {
        DataProductEntity entity = findOrThrow(id);
        String previous = entity.getLifecycleStatus();
        entity.setLifecycleStatus(newStatus);
        entity = dataProductRepository.save(entity);
        eventProducer.publishDataProductChanged("LIFECYCLE_CHANGED", entity, previous);
        return toResponse(entity);
    }

    @Transactional
    public void delete(UUID id) {
        DataProductEntity entity = findOrThrow(id);
        entity.setDeleted(true);
        dataProductRepository.save(entity);
        eventProducer.publishDataProductChanged("DELETED", entity);
    }

    @Transactional(readOnly = true)
    public List<DatasetResponse> getLinkedDatasets(UUID dataProductId) {
        findOrThrow(dataProductId);
        return portRepository.findByDataProductId(dataProductId).stream()
            .filter(p -> p.getDatasetId() != null)
            .map(p -> datasetRepository.findById(p.getDatasetId()).orElse(null))
            .filter(ds -> ds != null && !ds.isDeleted())
            .map(this::toDatasetResponse)
            .distinct()
            .toList();
    }

    @Transactional
    public void linkDataset(UUID dataProductId, UUID datasetId) {
        findOrThrow(dataProductId);
        boolean alreadyLinked = portRepository.findByDataProductId(dataProductId).stream()
            .anyMatch(p -> datasetId.equals(p.getDatasetId()));
        if (!alreadyLinked) {
            DataProductPortEntity port = new DataProductPortEntity();
            port.setDataProductId(dataProductId);
            port.setPortType("output");
            port.setDatasetId(datasetId);
            portRepository.save(port);
        }
    }

    @Transactional
    public void unlinkDataset(UUID dataProductId, UUID datasetId) {
        portRepository.deleteByDataProductIdAndDatasetId(dataProductId, datasetId);
    }

    private void applyRequest(DataProductEntity entity, DataProductRequest req, UUID tenantId) {
        entity.setTenantId(tenantId);
        entity.setTitle(req.title());
        entity.setDescription(req.description());
        entity.setDomainId(req.domainId());
        entity.setOwnerId(req.ownerId());
        entity.setLifecycleStatus(req.lifecycleStatus() != null ? req.lifecycleStatus() : "Ideation");
        entity.setPurpose(req.purpose());
        entity.setInformationSensitivity(req.informationSensitivity());
        entity.setKeywords(req.keywords());
        entity.setThemes(req.themes());
        entity.setLicense(req.license());
    }

    private DataProductEntity findOrThrow(UUID id) {
        return dataProductRepository.findById(id)
            .filter(e -> !e.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("DataProduct not found: " + id));
    }

    private DatasetResponse toDatasetResponse(com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity ds) {
        return new DatasetResponse(
            ds.getId(), ds.getTitle(), ds.getDescription(),
            ds.getCatalogId(), ds.getDomainId(), ds.getTenantId(),
            ds.getAccrualPeriodicity(), ds.getKeywords(), ds.getThemes(),
            ds.getLanguage(), ds.getLicense(), ds.getVersion(),
            ds.getSourceUri(), ds.isDeleted(), ds.getCreatedAt(), ds.getUpdatedAt(),
            ds.getOwnerId()
        );
    }

    DataProductResponse toResponse(DataProductEntity e) {
        return new DataProductResponse(
            e.getId(), e.getTitle(), e.getDescription(),
            e.getDomainId(), e.getTenantId(), e.getOwnerId(),
            e.getLifecycleStatus(), e.getPurpose(),
            e.getInformationSensitivity(), e.getKeywords(),
            e.getThemes(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
