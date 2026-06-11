package com.odin.catalog.policy.api.v1;

import com.odin.catalog.policy.api.v1.dto.*;
import com.odin.catalog.policy.application.PolicyEvaluationService;
import com.odin.catalog.policy.application.PolicyRegistryService;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@Tag(name = "Policies")
public class PolicyController {

    private static final Logger log = LoggerFactory.getLogger(PolicyController.class);

    private final PolicyRegistryService registryService;
    private final PolicyEvaluationService evaluationService;

    @Operation(summary = "Get policy for a dataset")
    @GetMapping("/{datasetId}")
    public PolicyResponse getPolicy(@PathVariable UUID datasetId) {
        return registryService.find(datasetId, tenantId())
            .map(registryService::toResponse)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No policy record found for dataset " + datasetId));
    }

    @Operation(summary = "Create or replace policy for a dataset")
    @PutMapping("/{datasetId}")
    public PolicyResponse upsertPolicy(@PathVariable UUID datasetId,
                                       @Valid @RequestBody PolicyUpsertRequest request) {
        log.info("action=UPSERT_POLICY datasetId={} level={}", datasetId, request.policyLevel());
        return registryService.toResponse(
            registryService.upsert(datasetId, tenantId(), request.policyJson(), request.policyLevel()));
    }

    @Operation(summary = "Delete policy for a dataset")
    @DeleteMapping("/{datasetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePolicy(@PathVariable UUID datasetId) {
        log.info("action=DELETE_POLICY datasetId={}", datasetId);
        registryService.delete(datasetId, tenantId());
    }

    @Operation(summary = "Evaluate dataset access policy",
        description = "Runs the ODRE enforcement algorithm against the stored ODRL policy. "
            + "Pass runtime context in M (e.g. callerRole, callerId) and F (coded functions, C-Level only). "
            + "Returns a UsageDecision: granted=true means the 'read' action passed all constraints.")
    @PostMapping("/{datasetId}/evaluate")
    public EvaluationResponse evaluate(@PathVariable UUID datasetId,
                                       @RequestBody EvaluationRequest request) {
        return evaluationService.evaluate(datasetId, tenantId(), request.M(), request.F());
    }

    @Operation(summary = "Get policy component breakdown for a dataset",
        description = "Returns the individual named policy pieces that compose this dataset's ODRL policy, "
            + "along with the assembled policy document. Components are keyed by piece type (CLASSIFICATION, "
            + "REGULATION, CONTRACTUAL) and dimension value.")
    @GetMapping("/{datasetId}/components")
    public PolicyComponentsResponse getComponents(@PathVariable UUID datasetId) {
        return registryService.getComponents(datasetId, tenantId());
    }

    @Operation(summary = "List recent evaluations for a dataset")
    @GetMapping("/{datasetId}/evaluations")
    public Page<EvaluationLogEntry> listEvaluations(@PathVariable UUID datasetId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return evaluationService.listEvaluations(datasetId, tenantId(), page, size);
    }

    private UUID tenantId() {
        String tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context not set");
        }
        return UUID.fromString(tenantId);
    }
}
