package com.odin.catalog.inventory.application.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.api.v1.dto.*;
import com.odin.catalog.inventory.infrastructure.jpa.entity.*;
import com.odin.catalog.inventory.infrastructure.jpa.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TermsPolicyService {

    private static final Logger log = LoggerFactory.getLogger(TermsPolicyService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final TermsPolicySetRepository policySetRepo;
    private final TermsClassificationRuleRepository classRuleRepo;
    private final TermsRegulationRuleRepository regRuleRepo;
    private final TermsRegulationObligationRepository regObligRepo;
    private final ObjectMapper objectMapper;

    // ── Active policy for TermsOfUseService ──────────────────────────────────

    @Cacheable("terms-active-policy")
    @Transactional(readOnly = true)
    public ActiveTermsPolicy getActivePolicy() {
        TermsPolicySetEntity set = policySetRepo.findFirstByStatus("ACTIVE")
            .orElseThrow(() -> new IllegalStateException("No active terms policy set found"));

        Map<String, ActiveTermsPolicy.ClassificationRule> classificationRules =
            classRuleRepo.findByPolicySetId(set.getId()).stream()
                .collect(Collectors.toMap(
                    TermsClassificationRuleEntity::getClassification,
                    e -> new ActiveTermsPolicy.ClassificationRule(
                        e.getRank(), e.getAccessLevel(),
                        parseList(e.getPermissions()), parseList(e.getProhibitions()),
                        parseList(e.getObligations()), parseList(e.getOdrlPermissions()),
                        parseList(e.getOdrlProhibitions()), parseList(e.getOdrlDuties())
                    )
                ));

        List<ActiveTermsPolicy.RegulationDetectionRule> regulationRules =
            regRuleRepo.findByPolicySetId(set.getId()).stream()
                .map(e -> new ActiveTermsPolicy.RegulationDetectionRule(
                    e.getSignalType(), e.getPattern(), e.getRegulationName(), e.getSignalLabel()))
                .toList();

        List<ActiveTermsPolicy.RegulationObligation> regulationObligations =
            regObligRepo.findByPolicySetId(set.getId()).stream()
                .map(e -> new ActiveTermsPolicy.RegulationObligation(
                    e.getRegulationName(), e.getObligation(), e.getOdrlDuty()))
                .toList();

        return new ActiveTermsPolicy(set.getId(), set.getName(),
            classificationRules, regulationRules, regulationObligations);
    }

    // ── Policy set CRUD ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TermsPolicySetResponse> listPolicySets() {
        return policySetRepo.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toSetResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public TermsPolicyDetailResponse getPolicySet(UUID id) {
        TermsPolicySetEntity set = findSetOrThrow(id);
        return toDetailResponse(set);
    }

    @Transactional
    public TermsPolicySetResponse createPolicySet(CreateTermsPolicyRequest req) {
        TermsPolicySetEntity set = new TermsPolicySetEntity();
        set.setName(req.name());
        set.setDescription(req.description());
        return toSetResponse(policySetRepo.save(set));
    }

    @Transactional
    public TermsPolicySetResponse updatePolicySet(UUID id, UpdateTermsPolicyRequest req) {
        TermsPolicySetEntity set = findSetOrThrow(id);
        set.setName(req.name());
        set.setDescription(req.description());
        set.setUpdatedAt(OffsetDateTime.now());
        return toSetResponse(policySetRepo.save(set));
    }

    @Transactional
    @CacheEvict(value = "terms-active-policy", allEntries = true)
    public TermsPolicySetResponse activatePolicySet(UUID id) {
        TermsPolicySetEntity target = findSetOrThrow(id);
        if ("ACTIVE".equals(target.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Policy set is already ACTIVE");
        }
        policySetRepo.findFirstByStatus("ACTIVE").ifPresent(current -> {
            current.setStatus("ARCHIVED");
            current.setUpdatedAt(OffsetDateTime.now());
            policySetRepo.save(current);
        });
        target.setStatus("ACTIVE");
        target.setEffectiveFrom(OffsetDateTime.now());
        target.setUpdatedAt(OffsetDateTime.now());
        log.info("action=TERMS_POLICY_ACTIVATED policySetId={} name={}", target.getId(), target.getName());
        return toSetResponse(policySetRepo.save(target));
    }

    @Transactional
    public void deletePolicySet(UUID id) {
        TermsPolicySetEntity set = findSetOrThrow(id);
        if (!"DRAFT".equals(set.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT policy sets may be deleted");
        }
        policySetRepo.delete(set);
    }

    @Transactional
    public TermsPolicySetResponse clonePolicySet(UUID sourceId, String newName) {
        TermsPolicySetEntity source = findSetOrThrow(sourceId);

        TermsPolicySetEntity clone = new TermsPolicySetEntity();
        clone.setName(newName);
        clone.setDescription(source.getDescription());
        clone.setVersion(source.getVersion() + 1);
        policySetRepo.save(clone);

        classRuleRepo.findByPolicySetId(sourceId).forEach(r -> {
            TermsClassificationRuleEntity copy = new TermsClassificationRuleEntity();
            copy.setPolicySetId(clone.getId());
            copy.setClassification(r.getClassification());
            copy.setRank(r.getRank());
            copy.setAccessLevel(r.getAccessLevel());
            copy.setPermissions(r.getPermissions());
            copy.setProhibitions(r.getProhibitions());
            copy.setObligations(r.getObligations());
            copy.setOdrlPermissions(r.getOdrlPermissions());
            copy.setOdrlProhibitions(r.getOdrlProhibitions());
            copy.setOdrlDuties(r.getOdrlDuties());
            classRuleRepo.save(copy);
        });

        regRuleRepo.findByPolicySetId(sourceId).forEach(r -> {
            TermsRegulationRuleEntity copy = new TermsRegulationRuleEntity();
            copy.setPolicySetId(clone.getId());
            copy.setSignalType(r.getSignalType());
            copy.setPattern(r.getPattern());
            copy.setRegulationName(r.getRegulationName());
            copy.setSignalLabel(r.getSignalLabel());
            regRuleRepo.save(copy);
        });

        regObligRepo.findByPolicySetId(sourceId).forEach(r -> {
            TermsRegulationObligationEntity copy = new TermsRegulationObligationEntity();
            copy.setPolicySetId(clone.getId());
            copy.setRegulationName(r.getRegulationName());
            copy.setObligation(r.getObligation());
            copy.setOdrlDuty(r.getOdrlDuty());
            regObligRepo.save(copy);
        });

        log.info("action=TERMS_POLICY_CLONED source={} clone={}", sourceId, clone.getId());
        return toSetResponse(clone);
    }

    // ── Classification rules ─────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "terms-active-policy", allEntries = true)
    public TermsClassificationRuleResponse upsertClassificationRule(
            UUID policySetId, String classification, UpsertClassificationRuleRequest req) {
        requireDraftOrThrow(findSetOrThrow(policySetId));
        TermsClassificationRuleEntity rule = classRuleRepo
            .findByPolicySetIdAndClassification(policySetId, classification)
            .orElseGet(() -> {
                TermsClassificationRuleEntity e = new TermsClassificationRuleEntity();
                e.setPolicySetId(policySetId);
                e.setClassification(classification);
                return e;
            });
        rule.setRank(req.rank());
        rule.setAccessLevel(req.accessLevel());
        rule.setPermissions(toJson(req.permissions()));
        rule.setProhibitions(toJson(req.prohibitions()));
        rule.setObligations(toJson(req.obligations()));
        rule.setOdrlPermissions(toJson(req.odrlPermissions()));
        rule.setOdrlProhibitions(toJson(req.odrlProhibitions()));
        rule.setOdrlDuties(toJson(req.odrlDuties()));
        return toClassRuleResponse(classRuleRepo.save(rule));
    }

    @Transactional
    @CacheEvict(value = "terms-active-policy", allEntries = true)
    public void deleteClassificationRule(UUID policySetId, String classification) {
        requireDraftOrThrow(findSetOrThrow(policySetId));
        classRuleRepo.deleteByPolicySetIdAndClassification(policySetId, classification);
    }

    // ── Regulation rules ─────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "terms-active-policy", allEntries = true)
    public TermsRegulationRuleResponse addRegulationRule(UUID policySetId, RegulationRuleRequest req) {
        requireDraftOrThrow(findSetOrThrow(policySetId));
        TermsRegulationRuleEntity rule = new TermsRegulationRuleEntity();
        rule.setPolicySetId(policySetId);
        rule.setSignalType(req.signalType());
        rule.setPattern(req.pattern());
        rule.setRegulationName(req.regulationName());
        rule.setSignalLabel(req.signalLabel());
        return toRegRuleResponse(regRuleRepo.save(rule));
    }

    @Transactional
    @CacheEvict(value = "terms-active-policy", allEntries = true)
    public TermsRegulationRuleResponse updateRegulationRule(UUID policySetId, UUID ruleId, RegulationRuleRequest req) {
        requireDraftOrThrow(findSetOrThrow(policySetId));
        TermsRegulationRuleEntity rule = regRuleRepo.findById(ruleId)
            .filter(r -> r.getPolicySetId().equals(policySetId))
            .orElseThrow(() -> new NoSuchElementException("Regulation rule not found: " + ruleId));
        rule.setSignalType(req.signalType());
        rule.setPattern(req.pattern());
        rule.setRegulationName(req.regulationName());
        rule.setSignalLabel(req.signalLabel());
        return toRegRuleResponse(regRuleRepo.save(rule));
    }

    @Transactional
    @CacheEvict(value = "terms-active-policy", allEntries = true)
    public void deleteRegulationRule(UUID policySetId, UUID ruleId) {
        requireDraftOrThrow(findSetOrThrow(policySetId));
        regRuleRepo.deleteById(ruleId);
    }

    // ── Regulation obligations ────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "terms-active-policy", allEntries = true)
    public TermsRegulationObligationResponse addRegulationObligation(UUID policySetId, RegulationObligationRequest req) {
        requireDraftOrThrow(findSetOrThrow(policySetId));
        TermsRegulationObligationEntity obl = new TermsRegulationObligationEntity();
        obl.setPolicySetId(policySetId);
        obl.setRegulationName(req.regulationName());
        obl.setObligation(req.obligation());
        obl.setOdrlDuty(req.odrlDuty());
        return toObligResponse(regObligRepo.save(obl));
    }

    @Transactional
    @CacheEvict(value = "terms-active-policy", allEntries = true)
    public void deleteRegulationObligation(UUID policySetId, UUID oblId) {
        requireDraftOrThrow(findSetOrThrow(policySetId));
        regObligRepo.deleteById(oblId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TermsPolicySetEntity findSetOrThrow(UUID id) {
        return policySetRepo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Terms policy set not found: " + id));
    }

    private static void requireDraftOrThrow(TermsPolicySetEntity set) {
        if (!"DRAFT".equals(set.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Policy set must be in DRAFT status to edit. Clone it first.");
        }
    }

    private TermsPolicySetResponse toSetResponse(TermsPolicySetEntity e) {
        return new TermsPolicySetResponse(e.getId(), e.getName(), e.getDescription(),
            e.getStatus(), e.getVersion(), e.getEffectiveFrom(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private TermsPolicyDetailResponse toDetailResponse(TermsPolicySetEntity set) {
        List<TermsClassificationRuleResponse> classRules = classRuleRepo.findByPolicySetId(set.getId()).stream()
            .map(this::toClassRuleResponse).toList();
        List<TermsRegulationRuleResponse> regRules = regRuleRepo.findByPolicySetId(set.getId()).stream()
            .map(this::toRegRuleResponse).toList();
        List<TermsRegulationObligationResponse> oblRules = regObligRepo.findByPolicySetId(set.getId()).stream()
            .map(this::toObligResponse).toList();
        return new TermsPolicyDetailResponse(set.getId(), set.getName(), set.getDescription(),
            set.getStatus(), set.getVersion(), set.getEffectiveFrom(),
            set.getCreatedAt(), set.getUpdatedAt(), classRules, regRules, oblRules);
    }

    private TermsClassificationRuleResponse toClassRuleResponse(TermsClassificationRuleEntity e) {
        return new TermsClassificationRuleResponse(e.getId(), e.getClassification(), e.getRank(), e.getAccessLevel(),
            parseList(e.getPermissions()), parseList(e.getProhibitions()), parseList(e.getObligations()),
            parseList(e.getOdrlPermissions()), parseList(e.getOdrlProhibitions()), parseList(e.getOdrlDuties()));
    }

    private TermsRegulationRuleResponse toRegRuleResponse(TermsRegulationRuleEntity e) {
        return new TermsRegulationRuleResponse(e.getId(), e.getSignalType(), e.getPattern(),
            e.getRegulationName(), e.getSignalLabel());
    }

    private TermsRegulationObligationResponse toObligResponse(TermsRegulationObligationEntity e) {
        return new TermsRegulationObligationResponse(e.getId(), e.getRegulationName(),
            e.getObligation(), e.getOdrlDuty());
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            log.warn("Failed to parse JSON list: {}", json);
            return List.of();
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
