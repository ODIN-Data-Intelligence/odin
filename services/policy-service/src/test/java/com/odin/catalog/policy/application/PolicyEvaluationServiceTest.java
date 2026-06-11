package com.odin.catalog.policy.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.policy.api.v1.dto.EvaluationResponse;
import com.odin.catalog.policy.infrastructure.jpa.EvaluationLogEntity;
import com.odin.catalog.policy.infrastructure.jpa.EvaluationLogRepository;
import com.odin.catalog.policy.infrastructure.jpa.PolicyRecordEntity;
import com.odin.catalog.policy.infrastructure.kafka.PolicyEventProducer;
import com.odin.catalog.policy.infrastructure.odre.OdreEngine;
import com.odin.catalog.policy.infrastructure.odre.OdrePolicy;
import com.odin.catalog.policy.infrastructure.odre.UsageDecisionTuple;
import com.odin.catalog.shared.models.policy.PolicyEvaluationResultPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyEvaluationServiceTest {

    static final UUID DATASET = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID TENANT  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    static final String POLICY_JSON = """
        {"@context":"http://www.w3.org/ns/odrl.jsonld","@type":"Set",
         "permission":[{"action":"read","target":"dataset:test"}]}
        """;

    @Mock PolicyRegistryService  registryService;
    @Mock EvaluationLogRepository logRepository;
    @Mock PolicyEventProducer    eventProducer;
    @Mock OdreEngine             odreEngine;
    @Spy  ObjectMapper           objectMapper = new ObjectMapper();

    @InjectMocks PolicyEvaluationService service;

    // ── evaluate — not found ──────────────────────────────────────────────

    @Test
    void evaluate_noPolicy_throwsNotFound() {
        when(registryService.find(DATASET, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluate(DATASET, TENANT, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── evaluate — engine failure ─────────────────────────────────────────

    @Test
    void evaluate_engineThrowsException_throwsUnprocessableEntity() {
        when(registryService.find(DATASET, TENANT))
            .thenReturn(Optional.of(record(POLICY_JSON)));
        when(odreEngine.enforce(any(), any(), any()))
            .thenThrow(new RuntimeException("SPARQL parse error"));

        assertThatThrownBy(() -> service.evaluate(DATASET, TENANT, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── evaluate — granted ────────────────────────────────────────────────

    @Test
    void evaluate_readDecisionGranted_returnsGrantedTrue() {
        when(registryService.find(DATASET, TENANT))
            .thenReturn(Optional.of(record(POLICY_JSON)));
        when(odreEngine.enforce(any(), any(), any()))
            .thenReturn(Set.of(new UsageDecisionTuple("read", "true")));
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EvaluationResponse result = service.evaluate(DATASET, TENANT, Map.of("role", "DATA_OWNER"), null);

        assertThat(result.granted()).isTrue();
        assertThat(result.decisions()).hasSize(1);
        assertThat(result.decisions().get(0).action()).isEqualTo("read");
        assertThat(result.decisions().get(0).delegated()).isFalse();
    }

    // ── evaluate — denied ─────────────────────────────────────────────────

    @Test
    void evaluate_noReadDecision_returnsGrantedFalse() {
        when(registryService.find(DATASET, TENANT))
            .thenReturn(Optional.of(record(POLICY_JSON)));
        when(odreEngine.enforce(any(), any(), any()))
            .thenReturn(Set.of(new UsageDecisionTuple("attribute", "attribute")));
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EvaluationResponse result = service.evaluate(DATASET, TENANT, null, null);

        assertThat(result.granted()).isFalse();
    }

    @Test
    void evaluate_emptyDecisionSet_returnsGrantedFalse() {
        when(registryService.find(DATASET, TENANT))
            .thenReturn(Optional.of(record(POLICY_JSON)));
        when(odreEngine.enforce(any(), any(), any())).thenReturn(Set.of());
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EvaluationResponse result = service.evaluate(DATASET, TENANT, null, null);

        assertThat(result.granted()).isFalse();
        assertThat(result.decisions()).isEmpty();
    }

    // ── evaluate — delegated obligations ─────────────────────────────────

    @Test
    void evaluate_obligationDecision_isDelegated() {
        when(registryService.find(DATASET, TENANT))
            .thenReturn(Optional.of(record(POLICY_JSON)));
        // obligation: action == value → delegated
        when(odreEngine.enforce(any(), any(), any()))
            .thenReturn(Set.of(new UsageDecisionTuple("read", "true"),
                               new UsageDecisionTuple("attribute", "attribute")));
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EvaluationResponse result = service.evaluate(DATASET, TENANT, null, null);

        assertThat(result.decisions()).anySatisfy(t -> {
            assertThat(t.action()).isEqualTo("attribute");
            assertThat(t.delegated()).isTrue();
        });
    }

    // ── evaluate — null M/F handled gracefully ────────────────────────────

    @Test
    void evaluate_nullMAndF_passesEmptyMapsToEngine() {
        when(registryService.find(DATASET, TENANT))
            .thenReturn(Optional.of(record(POLICY_JSON)));
        when(odreEngine.enforce(any(), eq(Map.of()), eq(Map.of())))
            .thenReturn(Set.of(new UsageDecisionTuple("read", "true")));
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.evaluate(DATASET, TENANT, null, null);

        verify(odreEngine).enforce(any(), eq(Map.of()), eq(Map.of()));
    }

    // ── side effects — log persisted ──────────────────────────────────────

    @Test
    void evaluate_always_persistsEvaluationLog() {
        when(registryService.find(DATASET, TENANT))
            .thenReturn(Optional.of(record(POLICY_JSON)));
        when(odreEngine.enforce(any(), any(), any()))
            .thenReturn(Set.of(new UsageDecisionTuple("read", "true")));
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.evaluate(DATASET, TENANT, Map.of("role", "viewer"), null);

        ArgumentCaptor<EvaluationLogEntity> captor = ArgumentCaptor.forClass(EvaluationLogEntity.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getDatasetId()).isEqualTo(DATASET);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().getAction()).isEqualTo("read");
        assertThat(captor.getValue().isGranted()).isTrue();
    }

    // ── side effects — Kafka event published ─────────────────────────────

    @Test
    void evaluate_always_publishesEvaluationEvent() {
        when(registryService.find(DATASET, TENANT))
            .thenReturn(Optional.of(record(POLICY_JSON)));
        when(odreEngine.enforce(any(), any(), any()))
            .thenReturn(Set.of(new UsageDecisionTuple("read", "true")));
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.evaluate(DATASET, TENANT, null, null);

        ArgumentCaptor<PolicyEvaluationResultPayload> captor =
            ArgumentCaptor.forClass(PolicyEvaluationResultPayload.class);
        verify(eventProducer).publishEvaluationCompleted(captor.capture());
        assertThat(captor.getValue().datasetId()).isEqualTo(DATASET.toString());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT.toString());
        assertThat(captor.getValue().granted()).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    static PolicyRecordEntity record(String json) {
        PolicyRecordEntity e = new PolicyRecordEntity();
        e.setId(UUID.randomUUID());
        e.setDatasetId(DATASET);
        e.setTenantId(TENANT);
        e.setPolicyLevel("A");
        e.setPolicyJson(json);
        return e;
    }
}
