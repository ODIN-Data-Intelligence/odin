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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributionServiceTest {

    static final UUID TENANT    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID DATASET   = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock DistributionRepository distributionRepository;
    @Mock CsvwColumnRepository   csvwColumnRepository;
    @Mock JdbcTemplate           jdbcTemplate;

    @InjectMocks DistributionService service;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.set(TENANT.toString());
    }

    // ── listAll ───────────────────────────────────────────────────────────

    @Test
    void listAll_returnsTenantPage() {
        DistributionEntity d = dist(DATASET, "Parquet");
        var pageable = PageRequest.of(0, 20);
        when(distributionRepository.findByTenantIdAndIsDeletedFalse(TENANT, pageable))
            .thenReturn(new PageImpl<>(List.of(d)));

        var result = service.listAll(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Parquet");
    }

    // ── listByDataset ─────────────────────────────────────────────────────

    @Test
    void listByDataset_returnsDistributionsForDataset() {
        when(distributionRepository.findByDatasetIdAndIsDeletedFalse(DATASET))
            .thenReturn(List.of(dist(DATASET, "CSV"), dist(DATASET, "JSON")));

        List<DistributionResponse> result = service.listByDataset(DATASET);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DistributionResponse::title)
            .containsExactlyInAnyOrder("CSV", "JSON");
    }

    // ── get ───────────────────────────────────────────────────────────────

    @Test
    void get_found_returnsMappedResponse() {
        DistributionEntity d = dist(DATASET, "Parquet snapshot");
        when(distributionRepository.findById(d.getId())).thenReturn(Optional.of(d));

        DistributionResponse result = service.get(d.getId());

        assertThat(result.id()).isEqualTo(d.getId());
        assertThat(result.title()).isEqualTo("Parquet snapshot");
        assertThat(result.datasetId()).isEqualTo(DATASET);
    }

    @Test
    void get_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(distributionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── create ────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_setsCorrectFields() {
        DistributionRequest req = new DistributionRequest(
            "My Dist", "desc", "https://access.example.com", "s3://bucket/file.parquet",
            "application/parquet", "Parquet", 1024L,
            "SHA-256", "abc123", null, null, "stable",
            "warehouse", "finance", "positions");

        DistributionEntity saved = dist(DATASET, "My Dist");
        when(distributionRepository.save(any())).thenReturn(saved);

        DistributionResponse result = service.create(DATASET, req);

        ArgumentCaptor<DistributionEntity> captor = ArgumentCaptor.forClass(DistributionEntity.class);
        verify(distributionRepository).save(captor.capture());
        DistributionEntity entity = captor.getValue();
        assertThat(entity.getTenantId()).isEqualTo(TENANT);
        assertThat(entity.getDatasetId()).isEqualTo(DATASET);
        assertThat(entity.getTitle()).isEqualTo("My Dist");
        assertThat(entity.getMediaType()).isEqualTo("application/parquet");
        assertThat(entity.getByteSize()).isEqualTo(1024L);
        assertThat(entity.getDatabaseName()).isEqualTo("warehouse");
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_found_appliesAllFieldsAndReturns() {
        DistributionEntity existing = dist(DATASET, "Old Title");
        when(distributionRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DistributionRequest req = new DistributionRequest(
            "New Title", null, null, "s3://new/path",
            "text/csv", "CSV", null, null, null, null, null, null, null, null, null);

        DistributionResponse result = service.update(existing.getId(), req);

        assertThat(result.title()).isEqualTo("New Title");
        assertThat(existing.getFormat()).isEqualTo("CSV");
    }

    @Test
    void update_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(distributionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id,
            new DistributionRequest("t", null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_found_softDeletesAndSaves() {
        DistributionEntity d = dist(DATASET, "To Delete");
        when(distributionRepository.findById(d.getId())).thenReturn(Optional.of(d));

        service.delete(d.getId());

        ArgumentCaptor<DistributionEntity> captor = ArgumentCaptor.forClass(DistributionEntity.class);
        verify(distributionRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
    }

    @Test
    void delete_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(distributionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── getPhysicalSchema ─────────────────────────────────────────────────

    @Test
    void getPhysicalSchema_returnsColumnsOrderedByOrdinal() {
        UUID schemaId = UUID.nameUUIDFromBytes((DATASET.toString() + ":schema").getBytes());
        CsvwColumnEntity col = column(schemaId, 1, "customer_id");
        when(csvwColumnRepository.findBySchemaIdOrderByOrdinalAsc(schemaId)).thenReturn(List.of(col));

        List<CsvwColumnResponse> result = service.getPhysicalSchema(DATASET);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("customer_id");
        assertThat(result.get(0).ordinal()).isEqualTo(1);
    }

    // ── setPhysicalSchema ─────────────────────────────────────────────────

    @Test
    void setPhysicalSchema_upsertsTables_deletesOldColumns_andSavesNew() {
        CsvwColumnRequest req = new CsvwColumnRequest(
            "account_id", "string", "Account identifier",
            true, List.of("Account ID"), "https://schema.org/identifier",
            null, null, null, null, null, null);

        UUID schemaId = UUID.nameUUIDFromBytes((DATASET.toString() + ":schema").getBytes());
        CsvwColumnEntity savedCol = column(schemaId, 1, "account_id");
        when(csvwColumnRepository.save(any())).thenReturn(savedCol);

        List<CsvwColumnResponse> result = service.setPhysicalSchema(DATASET, List.of(req));

        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(Object.class), any(Object.class));
        verify(csvwColumnRepository).deleteBySchemaId(schemaId);
        verify(csvwColumnRepository).save(any());

        ArgumentCaptor<CsvwColumnEntity> captor = ArgumentCaptor.forClass(CsvwColumnEntity.class);
        verify(csvwColumnRepository).save(captor.capture());
        CsvwColumnEntity entity = captor.getValue();
        assertThat(entity.getName()).isEqualTo("account_id");
        assertThat(entity.getOrdinal()).isEqualTo(1);
        assertThat(entity.getRequired()).isTrue();
        assertThat(entity.getPropertyUrl()).isEqualTo("https://schema.org/identifier");
    }

    @Test
    void setPhysicalSchema_multipleColumns_setsAscendingOrdinals() {
        UUID schemaId = UUID.nameUUIDFromBytes((DATASET.toString() + ":schema").getBytes());
        when(csvwColumnRepository.save(any())).thenAnswer(inv -> {
            CsvwColumnEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        List<CsvwColumnRequest> cols = List.of(
            new CsvwColumnRequest("col_a", null, null, null, null, null, null, null, null, null, null, null),
            new CsvwColumnRequest("col_b", null, null, null, null, null, null, null, null, null, null, null)
        );

        service.setPhysicalSchema(DATASET, cols);

        ArgumentCaptor<CsvwColumnEntity> captor = ArgumentCaptor.forClass(CsvwColumnEntity.class);
        verify(csvwColumnRepository, times(2)).save(captor.capture());
        List<CsvwColumnEntity> saved = captor.getAllValues();
        assertThat(saved.get(0).getOrdinal()).isEqualTo(1);
        assertThat(saved.get(1).getOrdinal()).isEqualTo(2);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    static DistributionEntity dist(UUID datasetId, String title) {
        DistributionEntity e = new DistributionEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setDatasetId(datasetId);
        e.setTitle(title);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    static CsvwColumnEntity column(UUID schemaId, int ordinal, String name) {
        CsvwColumnEntity e = new CsvwColumnEntity();
        e.setId(UUID.randomUUID());
        e.setSchemaId(schemaId);
        e.setOrdinal(ordinal);
        e.setName(name);
        return e;
    }
}
