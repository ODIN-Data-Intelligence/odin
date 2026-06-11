package com.odin.catalog.inventory.application.catalog;

import com.odin.catalog.inventory.api.v1.dto.CatalogRequest;
import com.odin.catalog.inventory.api.v1.dto.CatalogResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.CatalogEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.CatalogRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import com.odin.catalog.shared.models.dcat.DcatCatalog;
import com.odin.catalog.shared.models.dcat.DcatDataset;
import com.odin.catalog.shared.models.dcat.DcatResource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final CatalogRepository catalogRepository;
    private final DatasetRepository datasetRepository;

    @Transactional(readOnly = true)
    public List<CatalogResponse> list() {
        UUID tenantId = tenantId();
        return catalogRepository.findByTenantIdAndIsDeletedFalse(tenantId)
            .stream().map(CatalogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CatalogResponse get(UUID id) {
        return CatalogResponse.from(findOrThrow(id));
    }

    @Transactional
    public CatalogResponse create(CatalogRequest request) {
        UUID tenantId = tenantId();
        CatalogEntity catalog = new CatalogEntity();
        applyRequest(catalog, request, tenantId);
        CatalogEntity saved = catalogRepository.save(catalog);
        log.info("action=CREATE_CATALOG catalogId={} tenantId={} title={}", saved.getId(), tenantId, saved.getTitle());
        return CatalogResponse.from(saved);
    }

    @Transactional
    public CatalogResponse update(UUID id, CatalogRequest request) {
        CatalogEntity catalog = findOrThrow(id);
        applyRequest(catalog, request, catalog.getTenantId());
        CatalogEntity saved = catalogRepository.save(catalog);
        log.info("action=UPDATE_CATALOG catalogId={} tenantId={} title={}", saved.getId(), saved.getTenantId(), saved.getTitle());
        return CatalogResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        CatalogEntity catalog = findOrThrow(id);
        catalog.setDeleted(true);
        catalogRepository.save(catalog);
        log.info("action=DELETE_CATALOG catalogId={} tenantId={}", id, catalog.getTenantId());
    }

    private CatalogEntity findOrThrow(UUID id) {
        UUID tenantId = tenantId();
        return catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog not found: " + id));
    }

    private void applyRequest(CatalogEntity e, CatalogRequest req, UUID tenantId) {
        e.setTenantId(tenantId);
        e.setTitle(req.title());
        e.setDescription(req.description());
        e.setHomepage(req.homepage());
        e.setDomainId(req.domainId());
        e.setKeywords(req.keywords());
        e.setThemes(req.themes());
        e.setLanguage(req.language());
        e.setLicense(req.license());
    }

    @Transactional(readOnly = true)
    public DcatCatalog export(UUID id) {
        CatalogEntity catalog = findOrThrow(id);

        DcatResource resource = new DcatResource(
            catalog.getId().toString(), "CATALOG", catalog.getIri(),
            catalog.getTenantId().toString(), null,
            catalog.getTitle(), catalog.getDescription(),
            catalog.getLanguage(), catalog.getKeywords(), catalog.getThemes(),
            catalog.getIssued() != null ? catalog.getIssued().toString() : null,
            catalog.getUpdatedAt() != null ? catalog.getUpdatedAt().toString() : null,
            catalog.getLicense(), null, null, null, null, null, null,
            catalog.getSourceUri(), null
        );

        List<DcatDataset> datasets = datasetRepository
            .findByCatalogIdAndTenantIdAndIsDeletedFalse(id, catalog.getTenantId())
            .stream()
            .map(ds -> buildDcatDataset(ds))
            .toList();

        return new DcatCatalog(resource, catalog.getHomepage(), null, datasets);
    }

    private DcatDataset buildDcatDataset(DatasetEntity ds) {
        DcatResource r = new DcatResource(
            ds.getId().toString(), "DATASET", ds.getIri(),
            ds.getTenantId().toString(),
            ds.getDomainId() != null ? ds.getDomainId().toString() : null,
            ds.getTitle(), ds.getDescription(),
            ds.getLanguage(), ds.getKeywords(), ds.getThemes(),
            ds.getIssued() != null ? ds.getIssued().toString() : null,
            ds.getModified() != null ? ds.getModified().toString() : null,
            ds.getLicense(), null, null, null, null, null, null,
            ds.getSourceUri(), null
        );
        return new DcatDataset(r, ds.getAccrualPeriodicity(), null, null, null, ds.getVersion(), null, null, null, null);
    }

    private UUID tenantId() {
        return UUID.fromString(TenantContextHolder.get());
    }
}
