package com.odin.catalog.inventory.application.catalog;

import com.odin.catalog.inventory.api.v1.dto.CatalogRequest;
import com.odin.catalog.inventory.api.v1.dto.CatalogResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.CatalogEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.CatalogRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import com.odin.catalog.shared.models.dcat.DcatCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock CatalogRepository catalogRepository;
    @Mock DatasetRepository datasetRepository;

    @InjectMocks CatalogService service;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.set(TENANT.toString());
    }

    // ── list ──────────────────────────────────────────────────────────────

    @Test
    void list_returnsMappedResponses() {
        CatalogEntity cat = catalog("My Catalog");
        when(catalogRepository.findByTenantIdAndIsDeletedFalse(TENANT)).thenReturn(List.of(cat));

        List<CatalogResponse> result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("My Catalog");
        assertThat(result.get(0).tenantId()).isEqualTo(TENANT);
    }

    @Test
    void list_empty_returnsEmptyList() {
        when(catalogRepository.findByTenantIdAndIsDeletedFalse(TENANT)).thenReturn(List.of());

        assertThat(service.list()).isEmpty();
    }

    // ── get ───────────────────────────────────────────────────────────────

    @Test
    void get_found_returnsMappedResponse() {
        CatalogEntity cat = catalog("Finance Catalog");
        when(catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(cat.getId(), TENANT))
            .thenReturn(Optional.of(cat));

        CatalogResponse result = service.get(cat.getId());

        assertThat(result.id()).isEqualTo(cat.getId());
        assertThat(result.title()).isEqualTo("Finance Catalog");
    }

    @Test
    void get_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(id, TENANT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_savesEntityWithAllFields() {
        CatalogRequest req = new CatalogRequest(
            "New Catalog", "desc", "https://example.com",
            UUID.randomUUID(), List.of("finance"), List.of("fibo"), List.of("en"), "CC-BY");

        CatalogEntity saved = catalog("New Catalog");
        saved.setDescription("desc");
        when(catalogRepository.save(any())).thenReturn(saved);

        CatalogResponse result = service.create(req);

        ArgumentCaptor<CatalogEntity> captor = ArgumentCaptor.forClass(CatalogEntity.class);
        verify(catalogRepository).save(captor.capture());
        CatalogEntity entity = captor.getValue();
        assertThat(entity.getTenantId()).isEqualTo(TENANT);
        assertThat(entity.getTitle()).isEqualTo("New Catalog");
        assertThat(entity.getDescription()).isEqualTo("desc");
        assertThat(entity.getLicense()).isEqualTo("CC-BY");
        assertThat(result.title()).isEqualTo("New Catalog");
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_found_appliesAllFields() {
        CatalogEntity existing = catalog("Old Title");
        when(catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(existing.getId(), TENANT))
            .thenReturn(Optional.of(existing));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CatalogRequest req = new CatalogRequest(
            "New Title", "new desc", null, null, null, null, null, "MIT");

        CatalogResponse result = service.update(existing.getId(), req);

        assertThat(result.title()).isEqualTo("New Title");
        assertThat(existing.getDescription()).isEqualTo("new desc");
        assertThat(existing.getLicense()).isEqualTo("MIT");
    }

    @Test
    void update_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(id, TENANT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id,
            new CatalogRequest("t", null, null, null, null, null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_found_softDeletesEntity() {
        CatalogEntity cat = catalog("To Delete");
        when(catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(cat.getId(), TENANT))
            .thenReturn(Optional.of(cat));

        service.delete(cat.getId());

        ArgumentCaptor<CatalogEntity> captor = ArgumentCaptor.forClass(CatalogEntity.class);
        verify(catalogRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
    }

    @Test
    void delete_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(id, TENANT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── export ────────────────────────────────────────────────────────────

    @Test
    void export_found_returnsDcatCatalogWithDatasets() {
        CatalogEntity cat = catalog("Export Catalog");
        when(catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(cat.getId(), TENANT))
            .thenReturn(Optional.of(cat));

        DatasetEntity ds = dataset(cat.getId());
        when(datasetRepository.findByCatalogIdAndTenantIdAndIsDeletedFalse(cat.getId(), TENANT))
            .thenReturn(List.of(ds));

        DcatCatalog result = service.export(cat.getId());

        assertThat(result).isNotNull();
        assertThat(result.datasets()).hasSize(1);
        assertThat(result.resource().title()).isEqualTo("Export Catalog");
    }

    @Test
    void export_noDatasets_returnsEmptyCatalog() {
        CatalogEntity cat = catalog("Empty Catalog");
        when(catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(cat.getId(), TENANT))
            .thenReturn(Optional.of(cat));
        when(datasetRepository.findByCatalogIdAndTenantIdAndIsDeletedFalse(cat.getId(), TENANT))
            .thenReturn(List.of());

        DcatCatalog result = service.export(cat.getId());

        assertThat(result.datasets()).isEmpty();
    }

    @Test
    void export_datasetWithAllOptionalFields_populatesDcatResource() {
        CatalogEntity cat = catalog("Rich Catalog");
        when(catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(cat.getId(), TENANT))
            .thenReturn(Optional.of(cat));

        DatasetEntity ds = dataset(cat.getId());
        ds.setDomainId(UUID.randomUUID());
        ds.setIssued(OffsetDateTime.now());
        ds.setModified(OffsetDateTime.now());
        when(datasetRepository.findByCatalogIdAndTenantIdAndIsDeletedFalse(cat.getId(), TENANT))
            .thenReturn(List.of(ds));

        DcatCatalog result = service.export(cat.getId());

        assertThat(result.datasets()).hasSize(1);
        assertThat(result.datasets().get(0).resource().domainId()).isNotNull();
        assertThat(result.datasets().get(0).resource().issued()).isNotNull();
        assertThat(result.datasets().get(0).resource().modified()).isNotNull();
    }

    @Test
    void export_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(catalogRepository.findByIdAndTenantIdAndIsDeletedFalse(id, TENANT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.export(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    static CatalogEntity catalog(String title) {
        CatalogEntity e = new CatalogEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setTitle(title);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    static DatasetEntity dataset(UUID catalogId) {
        DatasetEntity e = new DatasetEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setTitle("Test Dataset");
        e.setCatalogId(catalogId);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }
}
