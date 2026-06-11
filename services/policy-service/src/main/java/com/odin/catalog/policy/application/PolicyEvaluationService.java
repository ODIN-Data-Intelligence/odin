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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PolicyEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluationService.class);

    private final PolicyRegistryService registryService;
    private final EvaluationLogRepository logRepository;
    private final PolicyEventProducer eventProducer;
    private final OdreEngine odreEngine;
    private final ObjectMapper objectMapper;

    @Transactional
    public EvaluationResponse evaluate(UUID datasetId, UUID tenantId,
                                       Map<String, Object> M, Map<String, Object> F) {
        long startMs = System.currentTimeMillis();
        PolicyRecordEntity record = registryService.find(datasetId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No policy record found for dataset " + datasetId));

        Set<UsageDecisionTuple> decision;
        try {
            OdrePolicy policy = OdrePolicy.load(record.getPolicyJson());
            decision = odreEngine.enforce(policy, M != null ? M : Map.of(), F != null ? F : Map.of());
        } catch (Exception e) {
            log.error("action=POLICY_EVALUATION_FAILED datasetId={} error={}", datasetId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Policy evaluation failed: " + e.getMessage());
        }

        boolean granted = decision.stream()
            .anyMatch(t -> t.action().equals("read") && !t.value().equals(t.action()));

        List<EvaluationResponse.DecisionTuple> tuples = decision.stream()
            .map(t -> new EvaluationResponse.DecisionTuple(
                t.action(),
                t.value().toString(),
                t.action().equals(t.value().toString())
            ))
            .toList();

        log.info("action=POLICY_EVALUATED datasetId={} tenantId={} granted={} decisions={} elapsed={}ms",
            datasetId, tenantId, granted, tuples.size(), System.currentTimeMillis() - startMs);

        persistLog(datasetId, tenantId, "read", granted, M);
        publishEvent(datasetId, tenantId, "read", granted, tuples);

        return new EvaluationResponse(granted, record.getPolicyLevel(), tuples);
    }

    private void persistLog(UUID datasetId, UUID tenantId, String action, boolean granted,
                            Map<String, Object> context) {
        EvaluationLogEntity entry = new EvaluationLogEntity();
        entry.setDatasetId(datasetId);
        entry.setTenantId(tenantId);
        entry.setAction(action);
        entry.setGranted(granted);
        try {
            entry.setRequestContext(context != null ? objectMapper.writeValueAsString(context) : null);
        } catch (Exception ignored) {}
        logRepository.save(entry);
    }

    private void publishEvent(UUID datasetId, UUID tenantId, String action, boolean granted,
                              List<EvaluationResponse.DecisionTuple> tuples) {
        List<PolicyEvaluationResultPayload.DecisionTuple> payloadTuples = tuples.stream()
            .map(t -> new PolicyEvaluationResultPayload.DecisionTuple(t.action(), t.result(), t.delegated()))
            .toList();

        eventProducer.publishEvaluationCompleted(new PolicyEvaluationResultPayload(
            datasetId.toString(), tenantId.toString(), action, granted, payloadTuples));
    }
}
