package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.api.v1.dto.*;
import com.odin.catalog.inventory.application.dataset.TermsPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Terms Policies", description = "Manage policy sets that drive terms-of-use derivation. Governance users author rules in DRAFT, then activate one set at a time.")
@RestController
@RequestMapping("/api/v1/terms-policies")
@RequiredArgsConstructor
public class TermsPolicyController {

    private static final Logger log = LoggerFactory.getLogger(TermsPolicyController.class);

    private final TermsPolicyService service;

    // ── Policy sets ──────────────────────────────────────────────────────────

    @Operation(summary = "List all policy sets")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of policy sets ordered by creation date descending"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth")
    })
    @GetMapping
    public List<TermsPolicySetResponse> list() {
        return service.listPolicySets();
    }

    @Operation(summary = "Get a policy set with all child rules")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Policy set detail"),
        @ApiResponse(responseCode = "404", description = "Not found")
    })
    @GetMapping("/{id}")
    public TermsPolicyDetailResponse get(@PathVariable UUID id) {
        return service.getPolicySet(id);
    }

    @Operation(summary = "Create a new DRAFT policy set")
    @ApiResponse(responseCode = "201", description = "Created")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TermsPolicySetResponse create(@RequestBody CreateTermsPolicyRequest req) {
        log.info("action=CREATE_POLICY_SET name={}", req.name());
        return service.createPolicySet(req);
    }

    @Operation(summary = "Update name and description of a policy set")
    @PutMapping("/{id}")
    public TermsPolicySetResponse update(@PathVariable UUID id, @RequestBody UpdateTermsPolicyRequest req) {
        log.info("action=UPDATE_POLICY_SET id={}", id);
        return service.updatePolicySet(id, req);
    }

    @Operation(summary = "Delete a DRAFT policy set (and all its child rules)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        log.info("action=DELETE_POLICY_SET id={}", id);
        service.deletePolicySet(id);
    }

    @Operation(summary = "Activate a policy set — archives the current ACTIVE one")
    @PostMapping("/{id}/activate")
    public TermsPolicySetResponse activate(@PathVariable UUID id) {
        log.info("action=ACTIVATE_POLICY_SET id={}", id);
        return service.activatePolicySet(id);
    }

    @Operation(summary = "Clone a policy set to a new DRAFT (deep copy of all child rules)")
    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public TermsPolicySetResponse clone(@PathVariable UUID id, @RequestBody CloneTermsPolicyRequest req) {
        log.info("action=CLONE_POLICY_SET sourceId={} name={}", id, req.name());
        return service.clonePolicySet(id, req.name());
    }

    // ── Classification rules ─────────────────────────────────────────────────

    @Operation(summary = "Upsert the classification rule for a given level (e.g. PUBLIC, INTERNAL)")
    @PutMapping("/{id}/classification-rules/{classification}")
    public TermsClassificationRuleResponse upsertClassificationRule(
            @PathVariable UUID id,
            @PathVariable String classification,
            @RequestBody UpsertClassificationRuleRequest req) {
        log.info("action=UPSERT_CLASSIFICATION_RULE policySetId={} classification={}", id, classification);
        return service.upsertClassificationRule(id, classification.toUpperCase(), req);
    }

    @Operation(summary = "Delete the classification rule for a given level")
    @DeleteMapping("/{id}/classification-rules/{classification}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClassificationRule(@PathVariable UUID id, @PathVariable String classification) {
        log.info("action=DELETE_CLASSIFICATION_RULE policySetId={} classification={}", id, classification);
        service.deleteClassificationRule(id, classification.toUpperCase());
    }

    // ── Regulation rules ─────────────────────────────────────────────────────

    @Operation(summary = "List regulation detection rules for a policy set")
    @GetMapping("/{id}/regulation-rules")
    public List<TermsRegulationRuleResponse> listRegulationRules(@PathVariable UUID id) {
        return service.getPolicySet(id).regulationRules();
    }

    @Operation(summary = "Add a regulation detection rule")
    @PostMapping("/{id}/regulation-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public TermsRegulationRuleResponse addRegulationRule(
            @PathVariable UUID id, @RequestBody RegulationRuleRequest req) {
        log.info("action=ADD_REGULATION_RULE policySetId={} regulation={}", id, req.regulationName());
        return service.addRegulationRule(id, req);
    }

    @Operation(summary = "Update a regulation detection rule")
    @PutMapping("/{id}/regulation-rules/{ruleId}")
    public TermsRegulationRuleResponse updateRegulationRule(
            @PathVariable UUID id, @PathVariable UUID ruleId, @RequestBody RegulationRuleRequest req) {
        log.info("action=UPDATE_REGULATION_RULE policySetId={} ruleId={}", id, ruleId);
        return service.updateRegulationRule(id, ruleId, req);
    }

    @Operation(summary = "Delete a regulation detection rule")
    @DeleteMapping("/{id}/regulation-rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRegulationRule(@PathVariable UUID id, @PathVariable UUID ruleId) {
        log.info("action=DELETE_REGULATION_RULE policySetId={} ruleId={}", id, ruleId);
        service.deleteRegulationRule(id, ruleId);
    }

    // ── Regulation obligations ────────────────────────────────────────────────

    @Operation(summary = "List regulation obligations for a policy set")
    @GetMapping("/{id}/regulation-obligations")
    public List<TermsRegulationObligationResponse> listRegulationObligations(@PathVariable UUID id) {
        return service.getPolicySet(id).regulationObligations();
    }

    @Operation(summary = "Add a regulation obligation")
    @PostMapping("/{id}/regulation-obligations")
    @ResponseStatus(HttpStatus.CREATED)
    public TermsRegulationObligationResponse addRegulationObligation(
            @PathVariable UUID id, @RequestBody RegulationObligationRequest req) {
        log.info("action=ADD_REGULATION_OBLIGATION policySetId={}", id);
        return service.addRegulationObligation(id, req);
    }

    @Operation(summary = "Delete a regulation obligation")
    @DeleteMapping("/{id}/regulation-obligations/{oblId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRegulationObligation(@PathVariable UUID id, @PathVariable UUID oblId) {
        log.info("action=DELETE_REGULATION_OBLIGATION policySetId={} oblId={}", id, oblId);
        service.deleteRegulationObligation(id, oblId);
    }
}
