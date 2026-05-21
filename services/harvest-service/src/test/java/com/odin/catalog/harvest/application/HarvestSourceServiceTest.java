package com.odin.catalog.harvest.application;

import com.odin.catalog.harvest.api.v1.dto.HarvestSourceRequest;
import com.odin.catalog.harvest.api.v1.dto.HarvestSourceResponse;
import com.odin.catalog.harvest.connector.HarvestConnector;
import com.odin.catalog.harvest.domain.source.HarvestSource;
import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestSourceEntity;
import com.odin.catalog.harvest.infrastructure.jpa.repository.HarvestSourceRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HarvestSourceServiceTest {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock HarvestSourceRepository sourceRepository;
    @Mock HarvestConnector connector;

    HarvestSourceService service;

    @BeforeEach
    void setUp() {
        when(connector.sourceType()).thenReturn("dcat_http");
        service = new HarvestSourceService(sourceRepository, List.of(connector));
        TenantContextHolder.set(TENANT.toString());
    }

    // ── list ─────────────────────────────────────────────────────────────

    @Test
    void list_noTypeFilter_returnsAllForTenant() {
        HarvestSourceEntity entity = source("dcat_http");
        when(sourceRepository.findByTenantId(TENANT)).thenReturn(List.of(entity));

        List<HarvestSourceResponse> result = service.list(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceType()).isEqualTo("dcat_http");
        verify(sourceRepository).findByTenantId(TENANT);
        verify(sourceRepository, never()).findByTenantIdAndSourceType(any(), any());
    }

    @Test
    void list_withTypeFilter_filtersSourceType() {
        when(sourceRepository.findByTenantIdAndSourceType(TENANT, "aws_glue")).thenReturn(List.of());

        List<HarvestSourceResponse> result = service.list("aws_glue");

        assertThat(result).isEmpty();
        verify(sourceRepository).findByTenantIdAndSourceType(TENANT, "aws_glue");
        verify(sourceRepository, never()).findByTenantId(any());
    }

    // ── get ──────────────────────────────────────────────────────────────

    @Test
    void get_found_returnsResponse() {
        HarvestSourceEntity entity = source("snowflake");
        when(sourceRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        HarvestSourceResponse result = service.get(entity.getId());

        assertThat(result.id()).isEqualTo(entity.getId());
        assertThat(result.sourceType()).isEqualTo("snowflake");
    }

    @Test
    void get_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(sourceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(id.toString());
    }

    // ── create ───────────────────────────────────────────────────────────

    @Test
    void create_persistsWithTenantAndReturnsResponse() {
        HarvestSourceRequest req = new HarvestSourceRequest(
            "DCAT EU Open Data", "dcat_http",
            "https://data.europa.eu/api/hub/search/feeds/datasets.rss",
            null, null, null, null
        );
        HarvestSourceEntity saved = source("dcat_http");
        saved.setName("DCAT EU Open Data");
        when(sourceRepository.save(any())).thenReturn(saved);

        HarvestSourceResponse result = service.create(req);

        ArgumentCaptor<HarvestSourceEntity> captor = ArgumentCaptor.forClass(HarvestSourceEntity.class);
        verify(sourceRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().getName()).isEqualTo("DCAT EU Open Data");
        assertThat(captor.getValue().getSourceType()).isEqualTo("dcat_http");
        assertThat(result.name()).isEqualTo("DCAT EU Open Data");
    }

    // ── update ───────────────────────────────────────────────────────────

    @Test
    void update_found_appliesChanges() {
        HarvestSourceEntity entity = source("dcat_http");
        when(sourceRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(sourceRepository.save(entity)).thenReturn(entity);

        HarvestSourceRequest req = new HarvestSourceRequest(
            "Renamed Source", "aws_glue", null, "us-east-1", "mydb", null, null
        );
        service.update(entity.getId(), req);

        assertThat(entity.getName()).isEqualTo("Renamed Source");
        assertThat(entity.getSourceType()).isEqualTo("aws_glue");
        assertThat(entity.getRegion()).isEqualTo("us-east-1");
        assertThat(entity.getDatabaseName()).isEqualTo("mydb");
    }

    @Test
    void update_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(sourceRepository.findById(id)).thenReturn(Optional.empty());

        HarvestSourceRequest req = new HarvestSourceRequest("x", "dcat_http", null, null, null, null, null);
        assertThatThrownBy(() -> service.update(id, req))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Test
    void delete_delegatesToRepository() {
        UUID id = UUID.randomUUID();

        service.delete(id);

        verify(sourceRepository).deleteById(id);
    }

    // ── testConnection ────────────────────────────────────────────────────

    @Test
    void testConnection_knownConnector_delegatesToConnector() {
        HarvestSourceEntity entity = source("dcat_http");
        when(sourceRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(connector.testConnection(any(HarvestSource.class))).thenReturn(true);

        boolean result = service.testConnection(entity.getId());

        assertThat(result).isTrue();
        verify(connector).testConnection(any(HarvestSource.class));
    }

    @Test
    void testConnection_unknownSourceType_returnsFalse() {
        HarvestSourceEntity entity = source("teradata");
        when(sourceRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        boolean result = service.testConnection(entity.getId());

        assertThat(result).isFalse();
        verify(connector, never()).testConnection(any());
    }

    @Test
    void testConnection_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(sourceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.testConnection(id))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private HarvestSourceEntity source(String type) {
        HarvestSourceEntity e = new HarvestSourceEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setName("Test Source");
        e.setSourceType(type);
        return e;
    }
}
