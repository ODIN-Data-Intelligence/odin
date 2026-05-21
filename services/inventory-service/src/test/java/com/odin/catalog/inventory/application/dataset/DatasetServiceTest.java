package com.odin.catalog.inventory.application.dataset;

import com.odin.catalog.inventory.api.v1.dto.DatasetRequest;
import com.odin.catalog.inventory.api.v1.dto.DatasetResponse;
import com.odin.catalog.inventory.api.v1.dto.PageResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock DatasetRepository datasetRepository;
    @Mock CatalogEventProducer eventProducer;

    @InjectMocks DatasetService service;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.set(TENANT.toString());
    }

    // ── list ───────────────────────────────────────────────────────────────

    @Test
    void list_noCatalogId_queriesAllByTenant() {
        DatasetEntity ds = dataset();
        Pageable pg = PageRequest.of(0, 20);
        when(datasetRepository.findByTenantIdAndIsDeletedFalse(TENANT, pg))
            .thenReturn(new PageImpl<>(List.of(ds)));

        PageResponse<DatasetResponse> result = service.list(null, null, pg);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(ds.getId());
        verify(datasetRepository).findByTenantIdAndIsDeletedFalse(TENANT, pg);
    }

    @Test
    void list_withCatalogId_filtersByCatalog() {
        UUID catalogId = UUID.randomUUID();
        Pageable pg = PageRequest.of(0, 10);
        when(datasetRepository.findByTenantIdAndCatalogIdAndIsDeletedFalse(TENANT, catalogId, pg))
            .thenReturn(new PageImpl<>(List.of()));

        service.list(catalogId, null, pg);

        verify(datasetRepository).findByTenantIdAndCatalogIdAndIsDeletedFalse(TENANT, catalogId, pg);
        verify(datasetRepository, never()).findByTenantIdAndIsDeletedFalse(any(), any());
    }

    // ── get ───────────────────────────────────────────────────────────────

    @Test
    void get_found_returnsResponse() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        DatasetResponse result = service.get(ds.getId());

        assertThat(result.id()).isEqualTo(ds.getId());
        assertThat(result.title()).isEqualTo("Trade Data");
    }

    @Test
    void get_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(datasetRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(id.toString());
    }

    @Test
    void get_softDeleted_throwsNoSuchElement() {
        DatasetEntity ds = dataset();
        ds.setDeleted(true);
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        assertThatThrownBy(() -> service.get(ds.getId()))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_appliesRequestFieldsAndPublishes() {
        DatasetRequest req = new DatasetRequest(
            "Trades", "desc", UUID.randomUUID(), UUID.randomUUID(),
            "daily", List.of("risk"), List.of("Finance"), List.of("en"),
            "MIT", "1.0", "s3://bucket/trades"
        );
        DatasetEntity saved = dataset();
        when(datasetRepository.save(any())).thenReturn(saved);

        DatasetResponse result = service.create(req);

        ArgumentCaptor<DatasetEntity> captor = ArgumentCaptor.forClass(DatasetEntity.class);
        verify(datasetRepository).save(captor.capture());
        DatasetEntity captured = captor.getValue();
        assertThat(captured.getTenantId()).isEqualTo(TENANT);
        assertThat(captured.getTitle()).isEqualTo("Trades");
        assertThat(captured.getLicense()).isEqualTo("MIT");
        assertThat(captured.getVersion()).isEqualTo("1.0");
        assertThat(captured.getSourceUri()).isEqualTo("s3://bucket/trades");
        verify(eventProducer).publishDatasetChanged("CREATED", saved);
        assertThat(result.id()).isEqualTo(saved.getId());
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_found_appliesChangesAndPublishes() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
        when(datasetRepository.save(ds)).thenReturn(ds);

        DatasetRequest req = new DatasetRequest(
            "Updated Title", null, null, null,
            null, null, null, null, null, "2.0", null
        );
        service.update(ds.getId(), req);

        assertThat(ds.getTitle()).isEqualTo("Updated Title");
        assertThat(ds.getVersion()).isEqualTo("2.0");
        verify(eventProducer).publishDatasetChanged("UPDATED", ds);
    }

    @Test
    void update_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(datasetRepository.findById(id)).thenReturn(Optional.empty());

        DatasetRequest req = new DatasetRequest("X", null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(id, req)).isInstanceOf(NoSuchElementException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_marksDeletedAndPublishes() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
        when(datasetRepository.save(ds)).thenReturn(ds);

        service.delete(ds.getId());

        assertThat(ds.isDeleted()).isTrue();
        verify(eventProducer).publishDatasetChanged("DELETED", ds);
    }

    @Test
    void delete_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(datasetRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(NoSuchElementException.class);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private DatasetEntity dataset() {
        DatasetEntity ds = new DatasetEntity();
        ds.setId(UUID.randomUUID());
        ds.setTenantId(TENANT);
        ds.setTitle("Trade Data");
        ds.setCreatedAt(OffsetDateTime.now());
        ds.setUpdatedAt(OffsetDateTime.now());
        return ds;
    }
}
