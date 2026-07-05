package com.odin.catalog.policy.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.policy.infrastructure.jpa.DatasetPolicyLinkEntity;
import com.odin.catalog.policy.infrastructure.jpa.DatasetPolicyLinkRepository;
import com.odin.catalog.policy.infrastructure.jpa.PolicyPieceEntity;
import com.odin.catalog.policy.infrastructure.jpa.PolicyPieceRepository;
import com.odin.catalog.policy.infrastructure.jpa.PolicyRecordEntity;
import com.odin.catalog.policy.infrastructure.jpa.PolicyRecordRepository;
import com.odin.catalog.shared.models.policy.PolicyComponentPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyRegistryServiceTest {

    static final UUID DATASET = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID TENANT  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock PolicyRecordRepository      repository;
    @Mock PolicyPieceRepository       pieceRepository;
    @Mock DatasetPolicyLinkRepository linkRepository;
    @Spy  ObjectMapper                objectMapper = new ObjectMapper();

    @InjectMocks PolicyRegistryService service;

    // ── upsert ────────────────────────────────────────────────────────────

    @Test
    void upsert_newRecord_createsAndSavesEntity() {
        when(repository.findByDatasetIdAndTenantId(DATASET, TENANT)).thenReturn(Optional.empty());
        PolicyRecordEntity saved = policyRecord("A", "{\"@type\":\"Set\"}");
        when(repository.save(any())).thenReturn(saved);

        PolicyRecordEntity result = service.upsert(DATASET, TENANT, "{\"@type\":\"Set\"}", "A");

        ArgumentCaptor<PolicyRecordEntity> captor = ArgumentCaptor.forClass(PolicyRecordEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDatasetId()).isEqualTo(DATASET);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().getPolicyLevel()).isEqualTo("A");
    }

    @Test
    void upsert_existingRecord_updatesInPlace() {
        PolicyRecordEntity existing = policyRecord("A", "{\"old\":true}");
        when(repository.findByDatasetIdAndTenantId(DATASET, TENANT)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        service.upsert(DATASET, TENANT, "{\"new\":true}", "B1");

        assertThat(existing.getPolicyJson()).isEqualTo("{\"new\":true}");
        assertThat(existing.getPolicyLevel()).isEqualTo("B1");
    }

    @Test
    void upsert_nullLevel_defaultsToA() {
        when(repository.findByDatasetIdAndTenantId(DATASET, TENANT)).thenReturn(Optional.empty());
        PolicyRecordEntity saved = policyRecord("A", "{}");
        when(repository.save(any())).thenReturn(saved);

        service.upsert(DATASET, TENANT, "{}", null);

        ArgumentCaptor<PolicyRecordEntity> captor = ArgumentCaptor.forClass(PolicyRecordEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPolicyLevel()).isEqualTo("A");
    }

    // ── upsertFromEvent ────────────────────────────────────────────────────

    @Test
    void upsertFromEvent_parsesStringsAndDelegates() {
        when(repository.findByDatasetIdAndTenantId(DATASET, TENANT)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(policyRecord("A", "{}"));

        service.upsertFromEvent(DATASET.toString(), TENANT.toString(), "{}");

        verify(repository).save(any());
    }

    // ── find ──────────────────────────────────────────────────────────────

    @Test
    void find_delegatesToRepository() {
        PolicyRecordEntity rec = policyRecord("A", "{}");
        when(repository.findByDatasetIdAndTenantId(DATASET, TENANT)).thenReturn(Optional.of(rec));

        Optional<PolicyRecordEntity> result = service.find(DATASET, TENANT);

        assertThat(result).isPresent();
        verify(repository).findByDatasetIdAndTenantId(DATASET, TENANT);
    }

    @Test
    void find_noRecord_returnsEmpty() {
        when(repository.findByDatasetIdAndTenantId(DATASET, TENANT)).thenReturn(Optional.empty());

        assertThat(service.find(DATASET, TENANT)).isEmpty();
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_callsDeleteByDatasetAndTenant() {
        service.delete(DATASET, TENANT);

        verify(repository).deleteByDatasetIdAndTenantId(DATASET, TENANT);
    }

    // ── upsertPiece ────────────────────────────────────────────────────────

    @Test
    void upsertPiece_newPiece_savesAndReturns() {
        when(pieceRepository.findByTenantIdAndPieceTypeAndDimensionKey(TENANT, "classification", "restricted"))
            .thenReturn(Optional.empty());

        PolicyPieceEntity saved = piece(TENANT, "classification", "restricted");
        when(pieceRepository.save(any())).thenReturn(saved);

        PolicyPieceEntity result = service.upsertPiece(TENANT, "classification", "restricted",
            "Restricted", "{\"permission\":[]}");

        ArgumentCaptor<PolicyPieceEntity> captor = ArgumentCaptor.forClass(PolicyPieceEntity.class);
        verify(pieceRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().getPieceType()).isEqualTo("classification");
        assertThat(captor.getValue().getDimensionKey()).isEqualTo("restricted");
        assertThat(captor.getValue().getPolicyLevel()).isEqualTo("A");
    }

    @Test
    void upsertPiece_existingPiece_updatesLabel() {
        PolicyPieceEntity existing = piece(TENANT, "classification", "restricted");
        existing.setLabel("Old Label");
        when(pieceRepository.findByTenantIdAndPieceTypeAndDimensionKey(TENANT, "classification", "restricted"))
            .thenReturn(Optional.of(existing));
        when(pieceRepository.save(any())).thenReturn(existing);

        service.upsertPiece(TENANT, "classification", "restricted", "New Label", "{}");

        assertThat(existing.getLabel()).isEqualTo("New Label");
    }

    // ── upsertFromComponents ───────────────────────────────────────────────

    @Test
    void upsertFromComponents_replacesLinksAndAssemblesPolicyRecord() {
        String permJson = "{\"permission\":[{\"action\":\"read\",\"target\":\"dataset:x\"}]}";
        PolicyComponentPayload component = new PolicyComponentPayload(
            "classification", "public", "Public", permJson);

        PolicyPieceEntity savedPiece = piece(TENANT, "classification", "public");
        savedPiece.setPolicyJson(permJson);
        when(pieceRepository.findByTenantIdAndPieceTypeAndDimensionKey(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(pieceRepository.save(any())).thenReturn(savedPiece);
        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByDatasetIdAndTenantId(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(policyRecord("A", "{}"));

        service.upsertFromComponents(DATASET.toString(), TENANT.toString(), List.of(component));

        verify(linkRepository).deleteByDatasetIdAndTenantId(DATASET, TENANT);
        verify(linkRepository).save(any(DatasetPolicyLinkEntity.class));
        verify(repository).save(any(PolicyRecordEntity.class));
    }

    // ── findLinks ─────────────────────────────────────────────────────────

    @Test
    void findLinks_delegatesToLinkRepository() {
        when(linkRepository.findByDatasetIdAndTenantId(DATASET, TENANT)).thenReturn(List.of());

        List<DatasetPolicyLinkEntity> result = service.findLinks(DATASET, TENANT);

        assertThat(result).isEmpty();
        verify(linkRepository).findByDatasetIdAndTenantId(DATASET, TENANT);
    }

    // ── assemblePolicy via upsertFromComponents ────────────────────────────

    @Test
    void assemblePolicy_combinesPermissionsFromMultiplePieces() {
        String perm1 = "{\"permission\":[{\"action\":\"read\"}]}";
        String perm2 = "{\"prohibition\":[{\"action\":\"distribute\"}]}";

        PolicyPieceEntity piece1 = piece(TENANT, "access", "allowed");
        piece1.setPolicyJson(perm1);
        PolicyPieceEntity piece2 = piece(TENANT, "purpose", "no-distribute");
        piece2.setPolicyJson(perm2);

        when(pieceRepository.findByTenantIdAndPieceTypeAndDimensionKey(eq(TENANT), eq("access"), any()))
            .thenReturn(Optional.of(piece1));
        when(pieceRepository.findByTenantIdAndPieceTypeAndDimensionKey(eq(TENANT), eq("purpose"), any()))
            .thenReturn(Optional.of(piece2));
        when(pieceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByDatasetIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        ArgumentCaptor<PolicyRecordEntity> captor = ArgumentCaptor.forClass(PolicyRecordEntity.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.upsertFromComponents(DATASET.toString(), TENANT.toString(), List.of(
            new PolicyComponentPayload("access", "allowed", "Allowed", perm1),
            new PolicyComponentPayload("purpose", "no-distribute", "No Distribute", perm2)
        ));

        String assembled = captor.getValue().getPolicyJson();
        assertThat(assembled).contains("permission");
        assertThat(assembled).contains("prohibition");
        assertThat(assembled).contains("http://www.w3.org/ns/odrl.jsonld");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    static PolicyRecordEntity policyRecord(String level, String json) {
        PolicyRecordEntity e = new PolicyRecordEntity();
        e.setId(UUID.randomUUID());
        e.setDatasetId(DATASET);
        e.setTenantId(TENANT);
        e.setPolicyLevel(level);
        e.setPolicyJson(json);
        return e;
    }

    static PolicyPieceEntity piece(UUID tenantId, String type, String key) {
        PolicyPieceEntity e = new PolicyPieceEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setPieceType(type);
        e.setDimensionKey(key);
        e.setPolicyJson("{}");
        e.setPolicyLevel("A");
        return e;
    }
}
