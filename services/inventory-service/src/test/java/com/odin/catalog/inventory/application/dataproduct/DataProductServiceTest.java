package com.odin.catalog.inventory.application.dataproduct;

import com.odin.catalog.inventory.api.v1.dto.DataProductRequest;
import com.odin.catalog.inventory.api.v1.dto.DataProductResponse;
import com.odin.catalog.inventory.api.v1.dto.PageResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DataProductEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DataProductPortEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DataProductPortRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DataProductRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataProductServiceTest {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock DataProductRepository dataProductRepository;
    @Mock DataProductPortRepository portRepository;
    @Mock DatasetRepository datasetRepository;
    @Mock CatalogEventProducer eventProducer;

    @InjectMocks DataProductService service;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.set(TENANT.toString());
    }

    // ── list ───────────────────────────────────────────────────────────────

    @Test
    void list_noFilters_queriesAllByTenant() {
        DataProductEntity dp = dataProduct();
        Pageable pg = PageRequest.of(0, 20);
        when(dataProductRepository.findByTenantIdAndIsDeletedFalse(TENANT, pg))
            .thenReturn(new PageImpl<>(List.of(dp)));

        PageResponse<DataProductResponse> result = service.list(null, null, pg);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(dp.getId());
        verify(dataProductRepository).findByTenantIdAndIsDeletedFalse(TENANT, pg);
    }

    @Test
    void list_withDomainId_filtersByDomain() {
        UUID domainId = UUID.randomUUID();
        Pageable pg = PageRequest.of(0, 10);
        when(dataProductRepository.findByTenantIdAndDomainIdAndIsDeletedFalse(TENANT, domainId, pg))
            .thenReturn(new PageImpl<>(List.of()));

        service.list(domainId, null, pg);

        verify(dataProductRepository).findByTenantIdAndDomainIdAndIsDeletedFalse(TENANT, domainId, pg);
        verify(dataProductRepository, never()).findByTenantIdAndIsDeletedFalse(any(), any());
    }

    @Test
    void list_withLifecycleStatus_filtersByStatus() {
        Pageable pg = PageRequest.of(0, 10);
        when(dataProductRepository.findByTenantIdAndLifecycleStatusAndIsDeletedFalse(TENANT, "Consume", pg))
            .thenReturn(new PageImpl<>(List.of()));

        service.list(null, "Consume", pg);

        verify(dataProductRepository).findByTenantIdAndLifecycleStatusAndIsDeletedFalse(TENANT, "Consume", pg);
    }

    // ── get ───────────────────────────────────────────────────────────────

    @Test
    void get_found_returnsResponse() {
        DataProductEntity dp = dataProduct();
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));

        DataProductResponse result = service.get(dp.getId());

        assertThat(result.id()).isEqualTo(dp.getId());
        assertThat(result.title()).isEqualTo("Test Product");
    }

    @Test
    void get_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(dataProductRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(id.toString());
    }

    @Test
    void get_softDeleted_throws() {
        DataProductEntity dp = dataProduct();
        dp.setDeleted(true);
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));

        assertThatThrownBy(() -> service.get(dp.getId()))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_savesEntityAndPublishesEvent() {
        DataProductRequest req = new DataProductRequest(
            "New Product", "desc", null, null, "Build",
            "purpose", "Internal", List.of("tag"), List.of(), "MIT"
        );
        DataProductEntity saved = dataProduct();
        when(dataProductRepository.save(any())).thenReturn(saved);

        DataProductResponse result = service.create(req);

        ArgumentCaptor<DataProductEntity> captor = ArgumentCaptor.forClass(DataProductEntity.class);
        verify(dataProductRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().getLifecycleStatus()).isEqualTo("Build");
        verify(eventProducer).publishDataProductChanged("CREATED", saved);
        assertThat(result.id()).isEqualTo(saved.getId());
    }

    @Test
    void create_nullLifecycleStatus_defaultsToIdeation() {
        DataProductRequest req = new DataProductRequest(
            "P", null, null, null, null, null, null, null, null, null
        );
        DataProductEntity saved = dataProduct();
        when(dataProductRepository.save(any())).thenReturn(saved);

        service.create(req);

        ArgumentCaptor<DataProductEntity> captor = ArgumentCaptor.forClass(DataProductEntity.class);
        verify(dataProductRepository).save(captor.capture());
        assertThat(captor.getValue().getLifecycleStatus()).isEqualTo("Ideation");
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_found_savesAndPublishes() {
        DataProductEntity dp = dataProduct();
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));
        when(dataProductRepository.save(dp)).thenReturn(dp);

        DataProductRequest req = new DataProductRequest(
            "Updated", null, null, null, "Consume", null, null, null, null, null
        );
        service.update(dp.getId(), req);

        verify(dataProductRepository).save(dp);
        verify(eventProducer).publishDataProductChanged("UPDATED", dp);
    }

    @Test
    void update_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(dataProductRepository.findById(id)).thenReturn(Optional.empty());

        DataProductRequest req = new DataProductRequest("X", null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(id, req)).isInstanceOf(NoSuchElementException.class);
    }

    // ── transitionLifecycle ───────────────────────────────────────────────

    @Test
    void transitionLifecycle_updatesStatusAndPublishes() {
        DataProductEntity dp = dataProduct();
        dp.setLifecycleStatus("Build");
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));
        when(dataProductRepository.save(dp)).thenReturn(dp);

        service.transitionLifecycle(dp.getId(), "Deploy");

        assertThat(dp.getLifecycleStatus()).isEqualTo("Deploy");
        verify(eventProducer).publishDataProductChanged(eq("LIFECYCLE_CHANGED"), eq(dp), eq("Build"));
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_marksDeletedAndPublishes() {
        DataProductEntity dp = dataProduct();
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));
        when(dataProductRepository.save(dp)).thenReturn(dp);

        service.delete(dp.getId());

        assertThat(dp.isDeleted()).isTrue();
        verify(eventProducer).publishDataProductChanged("DELETED", dp);
    }

    // ── getLinkedDatasets ─────────────────────────────────────────────────

    @Test
    void getLinkedDatasets_returnsActiveDatasets() {
        DataProductEntity dp = dataProduct();
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));

        DataProductPortEntity port = port(dp.getId(), UUID.randomUUID());
        DatasetEntity ds = dataset(port.getDatasetId());

        when(portRepository.findByDataProductId(dp.getId())).thenReturn(List.of(port));
        when(datasetRepository.findById(port.getDatasetId())).thenReturn(Optional.of(ds));

        List<?> result = service.getLinkedDatasets(dp.getId());

        assertThat(result).hasSize(1);
    }

    @Test
    void getLinkedDatasets_skipsDeletedDatasets() {
        DataProductEntity dp = dataProduct();
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));

        DataProductPortEntity port = port(dp.getId(), UUID.randomUUID());
        DatasetEntity ds = dataset(port.getDatasetId());
        ds.setDeleted(true);

        when(portRepository.findByDataProductId(dp.getId())).thenReturn(List.of(port));
        when(datasetRepository.findById(port.getDatasetId())).thenReturn(Optional.of(ds));

        assertThat(service.getLinkedDatasets(dp.getId())).isEmpty();
    }

    @Test
    void getLinkedDatasets_skipsPortsWithNullDatasetId() {
        DataProductEntity dp = dataProduct();
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));

        DataProductPortEntity port = new DataProductPortEntity();
        port.setDataProductId(dp.getId());
        port.setPortType("input");
        // datasetId intentionally null

        when(portRepository.findByDataProductId(dp.getId())).thenReturn(List.of(port));

        assertThat(service.getLinkedDatasets(dp.getId())).isEmpty();
    }

    @Test
    void getLinkedDatasets_deduplicatesSameDataset() {
        DataProductEntity dp = dataProduct();
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));

        UUID dsId = UUID.randomUUID();
        DataProductPortEntity port1 = port(dp.getId(), dsId);
        DataProductPortEntity port2 = port(dp.getId(), dsId);
        DatasetEntity ds = dataset(dsId);

        when(portRepository.findByDataProductId(dp.getId())).thenReturn(List.of(port1, port2));
        when(datasetRepository.findById(dsId)).thenReturn(Optional.of(ds));

        // With distinct(), should return only one entry
        List<?> result = service.getLinkedDatasets(dp.getId());
        assertThat(result).hasSize(1);
    }

    // ── linkDataset ───────────────────────────────────────────────────────

    @Test
    void linkDataset_whenNotLinked_savesNewPort() {
        DataProductEntity dp = dataProduct();
        UUID dsId = UUID.randomUUID();
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));
        when(portRepository.findByDataProductId(dp.getId())).thenReturn(List.of());

        service.linkDataset(dp.getId(), dsId);

        ArgumentCaptor<DataProductPortEntity> captor = ArgumentCaptor.forClass(DataProductPortEntity.class);
        verify(portRepository).save(captor.capture());
        assertThat(captor.getValue().getDatasetId()).isEqualTo(dsId);
        assertThat(captor.getValue().getPortType()).isEqualTo("output");
    }

    @Test
    void linkDataset_whenAlreadyLinked_doesNotSaveAgain() {
        DataProductEntity dp = dataProduct();
        UUID dsId = UUID.randomUUID();
        when(dataProductRepository.findById(dp.getId())).thenReturn(Optional.of(dp));
        when(portRepository.findByDataProductId(dp.getId())).thenReturn(List.of(port(dp.getId(), dsId)));

        service.linkDataset(dp.getId(), dsId);

        verify(portRepository, never()).save(any());
    }

    // ── unlinkDataset ─────────────────────────────────────────────────────

    @Test
    void unlinkDataset_delegatesToRepository() {
        UUID dpId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();

        service.unlinkDataset(dpId, dsId);

        verify(portRepository).deleteByDataProductIdAndDatasetId(dpId, dsId);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private DataProductEntity dataProduct() {
        DataProductEntity dp = new DataProductEntity();
        dp.setId(UUID.randomUUID());
        dp.setTenantId(TENANT);
        dp.setTitle("Test Product");
        dp.setLifecycleStatus("Ideation");
        dp.setCreatedAt(OffsetDateTime.now());
        dp.setUpdatedAt(OffsetDateTime.now());
        return dp;
    }

    private DataProductPortEntity port(UUID dpId, UUID dsId) {
        DataProductPortEntity p = new DataProductPortEntity();
        p.setId(UUID.randomUUID());
        p.setDataProductId(dpId);
        p.setPortType("output");
        p.setDatasetId(dsId);
        return p;
    }

    private DatasetEntity dataset(UUID id) {
        DatasetEntity ds = new DatasetEntity();
        ds.setId(id);
        ds.setTenantId(TENANT);
        ds.setTitle("Dataset");
        ds.setCreatedAt(OffsetDateTime.now());
        ds.setUpdatedAt(OffsetDateTime.now());
        return ds;
    }
}
