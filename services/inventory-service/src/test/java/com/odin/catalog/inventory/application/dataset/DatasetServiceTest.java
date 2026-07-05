package com.odin.catalog.inventory.application.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.api.v1.dto.DatasetAuditResponse;
import com.odin.catalog.inventory.api.v1.dto.DatasetRequest;
import com.odin.catalog.inventory.api.v1.dto.DatasetResponse;
import com.odin.catalog.inventory.api.v1.dto.OwnershipProposalResponse;
import com.odin.catalog.inventory.api.v1.dto.PageResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetAuditLogEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.OwnershipProposalEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetAuditLogRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.OwnershipProposalRepository;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import com.odin.catalog.shared.auth.filter.ApiKeyAuthenticationFilter.ApiKeyPrincipal;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.Spy;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock DatasetRepository datasetRepository;
    @Mock DatasetAuditLogRepository auditLogRepository;
    @Mock OwnershipProposalRepository proposalRepository;
    @Mock CatalogEventProducer eventProducer;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks DatasetService service;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.set(TENANT.toString());
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
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
            "MIT", "1.0", "s3://bucket/trades", null
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
            null, null, null, null, null, "2.0", null, null
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

        DatasetRequest req = new DatasetRequest("X", null, null, null, null, null, null, null, null, null, null, null);
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

    // ── list with sourceUri ────────────────────────────────────────────────

    @Test
    void list_withSourceUri_returnsMatchingDataset() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findBySourceUri("s3://bucket/data")).thenReturn(List.of(ds));

        PageResponse<DatasetResponse> result = service.list(null, "s3://bucket/data", PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(ds.getId());
    }

    @Test
    void list_withSourceUri_notFound_returnsEmpty() {
        when(datasetRepository.findBySourceUri("s3://missing")).thenReturn(List.of());

        PageResponse<DatasetResponse> result = service.list(null, "s3://missing", PageRequest.of(0, 20));

        assertThat(result.content()).isEmpty();
    }

    // ── assignOwner ───────────────────────────────────────────────────────

    @Test
    void assignOwner_noExistingOwner_setsOwnerIdAndReturns() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
        when(datasetRepository.save(ds)).thenReturn(ds);

        UUID userId = UUID.randomUUID();
        DatasetResponse result = service.assignOwner(ds.getId(), userId);

        assertThat(ds.getOwnerId()).isEqualTo(userId);
        assertThat(result.id()).isEqualTo(ds.getId());
    }

    @Test
    void assignOwner_alreadyHasOwner_throwsIllegalState() {
        DatasetEntity ds = dataset();
        ds.setOwnerId(UUID.randomUUID());
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        assertThatThrownBy(() -> service.assignOwner(ds.getId(), UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already has an owner");
    }

    // ── proposeTransfer ───────────────────────────────────────────────────

    @Test
    void proposeTransfer_noPendingProposals_createsProposal() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
        when(proposalRepository.findByDatasetIdAndStatus(ds.getId(), "PENDING")).thenReturn(List.of());

        OwnershipProposalEntity saved = proposal(ds.getId(), UUID.randomUUID());
        when(proposalRepository.save(any())).thenReturn(saved);

        OwnershipProposalResponse result = service.proposeTransfer(ds.getId(), saved.getProposedOwnerId());

        assertThat(result.datasetId()).isEqualTo(ds.getId());
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    void proposeTransfer_existingPendingProposal_cancelsOldCreatesNew() {
        DatasetEntity ds = dataset();
        OwnershipProposalEntity existing = proposal(ds.getId(), UUID.randomUUID());
        existing.setStatus("PENDING");
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
        when(proposalRepository.findByDatasetIdAndStatus(ds.getId(), "PENDING"))
            .thenReturn(List.of(existing));

        OwnershipProposalEntity saved = proposal(ds.getId(), UUID.randomUUID());
        when(proposalRepository.save(any())).thenReturn(saved);

        service.proposeTransfer(ds.getId(), UUID.randomUUID());

        assertThat(existing.getStatus()).isEqualTo("REJECTED");
        assertThat(existing.getResolvedAt()).isNotNull();
    }

    // ── approveTransfer ───────────────────────────────────────────────────

    @Test
    void approveTransfer_validPendingProposal_approvesAndChangesOwner() {
        UUID proposedOwner = UUID.randomUUID();
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        OwnershipProposalEntity prop = proposal(ds.getId(), proposedOwner);
        prop.setStatus("PENDING");
        when(proposalRepository.findByIdAndDatasetId(prop.getId(), ds.getId()))
            .thenReturn(Optional.of(prop));
        when(proposalRepository.save(any())).thenReturn(prop);
        when(datasetRepository.save(ds)).thenReturn(ds);

        DatasetResponse result = service.approveTransfer(ds.getId(), prop.getId(), "approved");

        assertThat(prop.getStatus()).isEqualTo("APPROVED");
        assertThat(prop.getNote()).isEqualTo("approved");
        assertThat(ds.getOwnerId()).isEqualTo(proposedOwner);
        assertThat(result.id()).isEqualTo(ds.getId());
    }

    @Test
    void approveTransfer_proposalNotPending_throwsIllegalState() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        OwnershipProposalEntity prop = proposal(ds.getId(), UUID.randomUUID());
        prop.setStatus("REJECTED");
        when(proposalRepository.findByIdAndDatasetId(prop.getId(), ds.getId()))
            .thenReturn(Optional.of(prop));

        assertThatThrownBy(() -> service.approveTransfer(ds.getId(), prop.getId(), null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no longer pending");
    }

    // ── rejectTransfer ────────────────────────────────────────────────────

    @Test
    void rejectTransfer_validPendingProposal_rejectsProposal() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        OwnershipProposalEntity prop = proposal(ds.getId(), UUID.randomUUID());
        prop.setStatus("PENDING");
        when(proposalRepository.findByIdAndDatasetId(prop.getId(), ds.getId()))
            .thenReturn(Optional.of(prop));
        when(proposalRepository.save(any())).thenReturn(prop);

        OwnershipProposalResponse result = service.rejectTransfer(ds.getId(), prop.getId(), "denied");

        assertThat(prop.getStatus()).isEqualTo("REJECTED");
        assertThat(prop.getNote()).isEqualTo("denied");
        assertThat(result.status()).isEqualTo("REJECTED");
    }

    @Test
    void rejectTransfer_proposalNotPending_throwsIllegalState() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        OwnershipProposalEntity prop = proposal(ds.getId(), UUID.randomUUID());
        prop.setStatus("APPROVED");
        when(proposalRepository.findByIdAndDatasetId(prop.getId(), ds.getId()))
            .thenReturn(Optional.of(prop));

        assertThatThrownBy(() -> service.rejectTransfer(ds.getId(), prop.getId(), null))
            .isInstanceOf(IllegalStateException.class);
    }

    // ── getPendingProposal ────────────────────────────────────────────────

    @Test
    void getPendingProposal_found_returnsProposal() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        OwnershipProposalEntity prop = proposal(ds.getId(), UUID.randomUUID());
        when(proposalRepository.findByDatasetIdAndStatus(ds.getId(), "PENDING"))
            .thenReturn(List.of(prop));

        Optional<OwnershipProposalResponse> result = service.getPendingProposal(ds.getId());

        assertThat(result).isPresent();
        assertThat(result.get().datasetId()).isEqualTo(ds.getId());
    }

    @Test
    void getPendingProposal_noneFound_returnsEmpty() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
        when(proposalRepository.findByDatasetIdAndStatus(ds.getId(), "PENDING"))
            .thenReturn(List.of());

        assertThat(service.getPendingProposal(ds.getId())).isEmpty();
    }

    // ── getHistory ────────────────────────────────────────────────────────

    @Test
    void getHistory_returnsPageOfAuditResponses() {
        DatasetEntity ds = dataset();
        Pageable pg = PageRequest.of(0, 10);
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        DatasetAuditLogEntity log = new DatasetAuditLogEntity();
        log.setId(UUID.randomUUID());
        log.setDatasetId(ds.getId());
        log.setEventType("CREATED");
        log.setCreatedAt(OffsetDateTime.now());
        when(auditLogRepository.findByDatasetIdOrderByCreatedAtDesc(ds.getId(), pg))
            .thenReturn(new PageImpl<>(List.of(log)));

        PageResponse<DatasetAuditResponse> result = service.getHistory(ds.getId(), pg);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).eventType()).isEqualTo("CREATED");
    }

    // ── currentUser — branch coverage ─────────────────────────────────────────

    @Test
    void create_withJwtPrincipal_executesNormally() {
        UUID callerId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("tok")
            .header("alg", "RS256")
            .claim("sub", callerId.toString())
            .claim("email", "caller@example.com")
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(jwt, null, List.of()));

        DatasetEntity saved = dataset();
        when(datasetRepository.save(any())).thenReturn(saved);

        service.create(new DatasetRequest("T", null, null, null, null, null, null, null, null, null, null, null));

        verify(datasetRepository).save(any());
    }

    @Test
    void create_withApiKeyPrincipal_executesNormally() {
        ApiKeyPrincipal akp = new ApiKeyPrincipal("key-1", TENANT.toString(),
            UUID.randomUUID().toString(), List.of("catalog:write"), true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(akp, null, List.of()));

        DatasetEntity saved = dataset();
        when(datasetRepository.save(any())).thenReturn(saved);

        service.create(new DatasetRequest("T", null, null, null, null, null, null, null, null, null, null, null));

        verify(datasetRepository).save(any());
    }

    @Test
    void create_withGenericPrincipal_executesNormally() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("generic-user", null, List.of()));

        DatasetEntity saved = dataset();
        when(datasetRepository.save(any())).thenReturn(saved);

        service.create(new DatasetRequest("T", null, null, null, null, null, null, null, null, null, null, null));

        verify(datasetRepository).save(any());
    }

    // ── list — sourceUri deleted branch ───────────────────────────────────────

    @Test
    void list_withSourceUri_datasetDeleted_returnsEmpty() {
        DatasetEntity ds = dataset();
        ds.setDeleted(true);
        when(datasetRepository.findBySourceUri("s3://deleted")).thenReturn(List.of(ds));

        PageResponse<DatasetResponse> result = service.list(null, "s3://deleted", PageRequest.of(0, 20));

        assertThat(result.content()).isEmpty();
    }

    // ── proposeTransfer — with JWT caller ─────────────────────────────────────

    @Test
    void proposeTransfer_withJwtPrincipal_setsProposedByIdFromToken() {
        UUID callerId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "RS256").claim("sub", callerId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(jwt, null, List.of()));

        DatasetEntity ds = dataset();
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
        when(proposalRepository.findByDatasetIdAndStatus(ds.getId(), "PENDING")).thenReturn(List.of());
        OwnershipProposalEntity saved = proposal(ds.getId(), UUID.randomUUID());
        when(proposalRepository.save(any())).thenReturn(saved);

        service.proposeTransfer(ds.getId(), saved.getProposedOwnerId());

        ArgumentCaptor<OwnershipProposalEntity> captor = ArgumentCaptor.forClass(OwnershipProposalEntity.class);
        verify(proposalRepository).save(captor.capture());
        assertThat(captor.getValue().getProposedById()).isEqualTo(callerId);
    }

    // ── approveTransfer — ownership guard ────────────────────────────────────

    @Test
    void approveTransfer_callerIsCurrentOwner_approvesNormally() {
        UUID ownerId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "RS256").claim("sub", ownerId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(jwt, null, List.of()));

        DatasetEntity ds = dataset();
        ds.setOwnerId(ownerId); // same as callerId → guard skips
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));
        when(datasetRepository.save(ds)).thenReturn(ds);

        OwnershipProposalEntity prop = proposal(ds.getId(), UUID.randomUUID());
        prop.setStatus("PENDING");
        when(proposalRepository.findByIdAndDatasetId(prop.getId(), ds.getId()))
            .thenReturn(Optional.of(prop));
        when(proposalRepository.save(any())).thenReturn(prop);

        DatasetResponse result = service.approveTransfer(ds.getId(), prop.getId(), "ok");

        assertThat(prop.getStatus()).isEqualTo("APPROVED");
        assertThat(result.id()).isEqualTo(ds.getId());
    }

    @Test
    void approveTransfer_callerIsNotOwner_throwsIllegalArgument() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "RS256").claim("sub", callerId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(jwt, null, List.of()));

        DatasetEntity ds = dataset();
        ds.setOwnerId(ownerId);
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        assertThatThrownBy(() -> service.approveTransfer(ds.getId(), UUID.randomUUID(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only the current owner");
    }

    // ── rejectTransfer — ownership guard ─────────────────────────────────────

    @Test
    void rejectTransfer_callerIsCurrentOwner_rejectsNormally() {
        UUID ownerId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "RS256").claim("sub", ownerId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(jwt, null, List.of()));

        DatasetEntity ds = dataset();
        ds.setOwnerId(ownerId);
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        OwnershipProposalEntity prop = proposal(ds.getId(), UUID.randomUUID());
        prop.setStatus("PENDING");
        when(proposalRepository.findByIdAndDatasetId(prop.getId(), ds.getId()))
            .thenReturn(Optional.of(prop));
        when(proposalRepository.save(any())).thenReturn(prop);

        OwnershipProposalResponse result = service.rejectTransfer(ds.getId(), prop.getId(), "denied");

        assertThat(prop.getStatus()).isEqualTo("REJECTED");
        assertThat(result.status()).isEqualTo("REJECTED");
    }

    @Test
    void rejectTransfer_callerIsNotOwner_throwsIllegalArgument() {
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "RS256").claim("sub", callerId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(jwt, null, List.of()));

        DatasetEntity ds = dataset();
        ds.setOwnerId(ownerId);
        when(datasetRepository.findById(ds.getId())).thenReturn(Optional.of(ds));

        assertThatThrownBy(() -> service.rejectTransfer(ds.getId(), UUID.randomUUID(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only the current owner");
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

    private OwnershipProposalEntity proposal(UUID datasetId, UUID proposedOwnerId) {
        OwnershipProposalEntity e = new OwnershipProposalEntity();
        e.setId(UUID.randomUUID());
        e.setDatasetId(datasetId);
        e.setProposedOwnerId(proposedOwnerId);
        e.setTenantId(TENANT);
        e.setStatus("PENDING");
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }
}
