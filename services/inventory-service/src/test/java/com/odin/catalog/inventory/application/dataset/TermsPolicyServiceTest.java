package com.odin.catalog.inventory.application.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.api.v1.dto.*;
import com.odin.catalog.inventory.infrastructure.jpa.entity.*;
import com.odin.catalog.inventory.infrastructure.jpa.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TermsPolicyServiceTest {

    @Mock TermsPolicySetRepository          policySetRepo;
    @Mock TermsClassificationRuleRepository classRuleRepo;
    @Mock TermsRegulationRuleRepository     regRuleRepo;
    @Mock TermsRegulationObligationRepository regObligRepo;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks TermsPolicyService service;

    // ── getActivePolicy ───────────────────────────────────────────────────────

    @Test
    void getActivePolicy_noActiveSet_throwsIllegalState() {
        when(policySetRepo.findFirstByStatus("ACTIVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActivePolicy())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No active terms policy set found");
    }

    @Test
    void getActivePolicy_buildsActivePolicyFromAllChildRows() {
        TermsPolicySetEntity set = policySet("Default", "ACTIVE");

        TermsClassificationRuleEntity classRule = new TermsClassificationRuleEntity();
        classRule.setId(UUID.randomUUID());
        classRule.setPolicySetId(set.getId());
        classRule.setClassification("PUBLIC");
        classRule.setRank(0);
        classRule.setAccessLevel("OPEN");
        classRule.setPermissions("[\"Read data\"]");
        classRule.setProhibitions("[]");
        classRule.setObligations("[]");
        classRule.setOdrlPermissions("[\"odrl:read\"]");
        classRule.setOdrlProhibitions("[]");
        classRule.setOdrlDuties("[]");

        TermsRegulationRuleEntity regRule = new TermsRegulationRuleEntity();
        regRule.setId(UUID.randomUUID());
        regRule.setPolicySetId(set.getId());
        regRule.setSignalType("IRI_CONTAINS");
        regRule.setPattern("fibo-fbc");
        regRule.setRegulationName("MiFID II");
        regRule.setSignalLabel("FIBO Banking concepts");

        TermsRegulationObligationEntity obligation = new TermsRegulationObligationEntity();
        obligation.setId(UUID.randomUUID());
        obligation.setPolicySetId(set.getId());
        obligation.setRegulationName("MiFID II");
        obligation.setObligation("File regulatory reports");
        obligation.setOdrlDuty("filingDuty");

        when(policySetRepo.findFirstByStatus("ACTIVE")).thenReturn(Optional.of(set));
        when(classRuleRepo.findByPolicySetId(set.getId())).thenReturn(List.of(classRule));
        when(regRuleRepo.findByPolicySetId(set.getId())).thenReturn(List.of(regRule));
        when(regObligRepo.findByPolicySetId(set.getId())).thenReturn(List.of(obligation));

        ActiveTermsPolicy policy = service.getActivePolicy();

        assertThat(policy.policySetId()).isEqualTo(set.getId());
        assertThat(policy.classificationRules()).containsKey("PUBLIC");
        assertThat(policy.classificationRules().get("PUBLIC").accessLevel()).isEqualTo("OPEN");
        assertThat(policy.classificationRules().get("PUBLIC").permissions()).containsExactly("Read data");
        assertThat(policy.classificationRules().get("PUBLIC").odrlPermissions()).containsExactly("odrl:read");
        assertThat(policy.regulationRules()).hasSize(1);
        assertThat(policy.regulationRules().get(0).regulationName()).isEqualTo("MiFID II");
        assertThat(policy.regulationObligations()).hasSize(1);
        assertThat(policy.regulationObligations().get(0).odrlDuty()).isEqualTo("filingDuty");
    }

    // ── createPolicySet ───────────────────────────────────────────────────────

    @Test
    void createPolicySet_savesAndReturnsResponse() {
        TermsPolicySetEntity saved = policySet("New Policy", "DRAFT");
        when(policySetRepo.save(any())).thenReturn(saved);

        TermsPolicySetResponse result = service.createPolicySet(new CreateTermsPolicyRequest("New Policy", "desc"));

        ArgumentCaptor<TermsPolicySetEntity> captor = ArgumentCaptor.forClass(TermsPolicySetEntity.class);
        verify(policySetRepo).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New Policy");
        assertThat(captor.getValue().getDescription()).isEqualTo("desc");
        assertThat(result.name()).isEqualTo("New Policy");
        assertThat(result.status()).isEqualTo("DRAFT");
    }

    // ── updatePolicySet ───────────────────────────────────────────────────────

    @Test
    void updatePolicySet_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(policySetRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePolicySet(id, new UpdateTermsPolicyRequest("X", null)))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updatePolicySet_updatesNameAndDescription() {
        TermsPolicySetEntity set = policySet("Old", "DRAFT");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));
        when(policySetRepo.save(set)).thenReturn(set);

        service.updatePolicySet(set.getId(), new UpdateTermsPolicyRequest("New Name", "new desc"));

        assertThat(set.getName()).isEqualTo("New Name");
        assertThat(set.getDescription()).isEqualTo("new desc");
        verify(policySetRepo).save(set);
    }

    // ── activatePolicySet ─────────────────────────────────────────────────────

    @Test
    void activatePolicySet_alreadyActive_throws409() {
        TermsPolicySetEntity set = policySet("Policy", "ACTIVE");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));

        assertThatThrownBy(() -> service.activatePolicySet(set.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already ACTIVE");
    }

    @Test
    void activatePolicySet_archivesCurrentActiveAndPromotesTarget() {
        TermsPolicySetEntity current = policySet("Current", "ACTIVE");
        TermsPolicySetEntity target  = policySet("Target",  "DRAFT");

        when(policySetRepo.findById(target.getId())).thenReturn(Optional.of(target));
        when(policySetRepo.findFirstByStatus("ACTIVE")).thenReturn(Optional.of(current));
        when(policySetRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activatePolicySet(target.getId());

        assertThat(current.getStatus()).isEqualTo("ARCHIVED");
        assertThat(target.getStatus()).isEqualTo("ACTIVE");
        assertThat(target.getEffectiveFrom()).isNotNull();
        verify(policySetRepo, times(2)).save(any());
    }

    @Test
    void activatePolicySet_noExistingActive_promotesTargetWithoutArchiving() {
        TermsPolicySetEntity target = policySet("Target", "DRAFT");

        when(policySetRepo.findById(target.getId())).thenReturn(Optional.of(target));
        when(policySetRepo.findFirstByStatus("ACTIVE")).thenReturn(Optional.empty());
        when(policySetRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activatePolicySet(target.getId());

        assertThat(target.getStatus()).isEqualTo("ACTIVE");
        verify(policySetRepo, times(1)).save(target);
    }

    // ── deletePolicySet ───────────────────────────────────────────────────────

    @Test
    void deletePolicySet_nonDraft_throws409() {
        TermsPolicySetEntity set = policySet("Active Policy", "ACTIVE");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));

        assertThatThrownBy(() -> service.deletePolicySet(set.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only DRAFT");
    }

    @Test
    void deletePolicySet_draft_deletesEntity() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));

        service.deletePolicySet(set.getId());

        verify(policySetRepo).delete(set);
    }

    // ── clonePolicySet ────────────────────────────────────────────────────────

    @Test
    void clonePolicySet_copiesAllChildRows() {
        TermsPolicySetEntity source = policySet("Source", "ACTIVE");
        source.setVersion(2);

        TermsClassificationRuleEntity cr = new TermsClassificationRuleEntity();
        cr.setId(UUID.randomUUID());
        cr.setPolicySetId(source.getId());
        cr.setClassification("PUBLIC");
        cr.setRank(0);
        cr.setAccessLevel("OPEN");
        cr.setPermissions("[\"Read\"]");
        cr.setProhibitions("[]");
        cr.setObligations("[]");
        cr.setOdrlPermissions("[]");
        cr.setOdrlProhibitions("[]");
        cr.setOdrlDuties("[]");

        TermsRegulationRuleEntity rr = new TermsRegulationRuleEntity();
        rr.setId(UUID.randomUUID());
        rr.setPolicySetId(source.getId());
        rr.setSignalType("KEYWORD");
        rr.setPattern("gdpr");
        rr.setRegulationName("GDPR");
        rr.setSignalLabel("GDPR keyword");

        TermsRegulationObligationEntity ro = new TermsRegulationObligationEntity();
        ro.setId(UUID.randomUUID());
        ro.setPolicySetId(source.getId());
        ro.setRegulationName("GDPR");
        ro.setObligation("Conduct DPIA");
        ro.setOdrlDuty("conductDPIA");

        when(policySetRepo.findById(source.getId())).thenReturn(Optional.of(source));
        when(classRuleRepo.findByPolicySetId(source.getId())).thenReturn(List.of(cr));
        when(regRuleRepo.findByPolicySetId(source.getId())).thenReturn(List.of(rr));
        when(regObligRepo.findByPolicySetId(source.getId())).thenReturn(List.of(ro));
        when(policySetRepo.save(any())).thenAnswer(inv -> {
            TermsPolicySetEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
        when(classRuleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(regRuleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(regObligRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TermsPolicySetResponse result = service.clonePolicySet(source.getId(), "Clone");

        assertThat(result.name()).isEqualTo("Clone");
        assertThat(result.version()).isEqualTo(3);
        assertThat(result.status()).isEqualTo("DRAFT");

        ArgumentCaptor<TermsClassificationRuleEntity> crCaptor = ArgumentCaptor.forClass(TermsClassificationRuleEntity.class);
        verify(classRuleRepo).save(crCaptor.capture());
        assertThat(crCaptor.getValue().getClassification()).isEqualTo("PUBLIC");
        assertThat(crCaptor.getValue().getPolicySetId()).isNotEqualTo(source.getId());

        ArgumentCaptor<TermsRegulationRuleEntity> rrCaptor = ArgumentCaptor.forClass(TermsRegulationRuleEntity.class);
        verify(regRuleRepo).save(rrCaptor.capture());
        assertThat(rrCaptor.getValue().getRegulationName()).isEqualTo("GDPR");

        ArgumentCaptor<TermsRegulationObligationEntity> roCaptor = ArgumentCaptor.forClass(TermsRegulationObligationEntity.class);
        verify(regObligRepo).save(roCaptor.capture());
        assertThat(roCaptor.getValue().getOdrlDuty()).isEqualTo("conductDPIA");
    }

    // ── upsertClassificationRule ──────────────────────────────────────────────

    @Test
    void upsertClassificationRule_nonDraftPolicy_throws409() {
        TermsPolicySetEntity set = policySet("Active", "ACTIVE");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));

        assertThatThrownBy(() -> service.upsertClassificationRule(set.getId(), "PUBLIC",
            new UpsertClassificationRuleRequest(0, "OPEN", List.of(), List.of(), List.of(), List.of(), List.of(), List.of())))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("DRAFT status");
    }

    @Test
    void upsertClassificationRule_newRule_createsAndReturns() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));
        when(classRuleRepo.findByPolicySetIdAndClassification(set.getId(), "PUBLIC")).thenReturn(Optional.empty());

        TermsClassificationRuleEntity saved = new TermsClassificationRuleEntity();
        saved.setId(UUID.randomUUID());
        saved.setPolicySetId(set.getId());
        saved.setClassification("PUBLIC");
        saved.setRank(0);
        saved.setAccessLevel("OPEN");
        saved.setPermissions("[\"Read data\"]");
        saved.setProhibitions("[]");
        saved.setObligations("[]");
        saved.setOdrlPermissions("[]");
        saved.setOdrlProhibitions("[]");
        saved.setOdrlDuties("[]");
        when(classRuleRepo.save(any())).thenReturn(saved);

        TermsClassificationRuleResponse result = service.upsertClassificationRule(set.getId(), "PUBLIC",
            new UpsertClassificationRuleRequest(0, "OPEN", List.of("Read data"), List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThat(result.classification()).isEqualTo("PUBLIC");
        assertThat(result.accessLevel()).isEqualTo("OPEN");
        assertThat(result.permissions()).containsExactly("Read data");
    }

    @Test
    void upsertClassificationRule_existingRule_updatesInPlace() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");

        TermsClassificationRuleEntity existing = new TermsClassificationRuleEntity();
        existing.setId(UUID.randomUUID());
        existing.setPolicySetId(set.getId());
        existing.setClassification("PUBLIC");
        existing.setRank(0);
        existing.setAccessLevel("OPEN");
        existing.setPermissions("[]");
        existing.setProhibitions("[]");
        existing.setObligations("[]");
        existing.setOdrlPermissions("[]");
        existing.setOdrlProhibitions("[]");
        existing.setOdrlDuties("[]");

        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));
        when(classRuleRepo.findByPolicySetIdAndClassification(set.getId(), "PUBLIC")).thenReturn(Optional.of(existing));
        when(classRuleRepo.save(any())).thenReturn(existing);

        service.upsertClassificationRule(set.getId(), "PUBLIC",
            new UpsertClassificationRuleRequest(0, "RESTRICTED", List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThat(existing.getAccessLevel()).isEqualTo("RESTRICTED");
        verify(classRuleRepo).save(existing);
    }

    // ── addRegulationRule ─────────────────────────────────────────────────────

    @Test
    void addRegulationRule_nonDraft_throws409() {
        TermsPolicySetEntity set = policySet("Active", "ACTIVE");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));

        assertThatThrownBy(() -> service.addRegulationRule(set.getId(),
            new RegulationRuleRequest("IRI_CONTAINS", "fibo-fbc", "MiFID", "FIBO signal")))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void addRegulationRule_draft_savesAndReturns() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");

        TermsRegulationRuleEntity saved = new TermsRegulationRuleEntity();
        saved.setId(UUID.randomUUID());
        saved.setPolicySetId(set.getId());
        saved.setSignalType("KEYWORD");
        saved.setPattern("gdpr");
        saved.setRegulationName("GDPR");
        saved.setSignalLabel("GDPR keyword");

        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));
        when(regRuleRepo.save(any())).thenReturn(saved);

        TermsRegulationRuleResponse result = service.addRegulationRule(set.getId(),
            new RegulationRuleRequest("KEYWORD", "gdpr", "GDPR", "GDPR keyword"));

        assertThat(result.signalType()).isEqualTo("KEYWORD");
        assertThat(result.regulationName()).isEqualTo("GDPR");
    }

    // ── updateRegulationRule ──────────────────────────────────────────────────

    @Test
    void updateRegulationRule_ruleNotInPolicy_throws() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");
        UUID otherId = UUID.randomUUID();

        TermsRegulationRuleEntity rule = new TermsRegulationRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setPolicySetId(UUID.randomUUID()); // different policy set

        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));
        when(regRuleRepo.findById(otherId)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.updateRegulationRule(set.getId(), otherId,
            new RegulationRuleRequest("KEYWORD", "x", "Y", "Z")))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── addRegulationObligation ───────────────────────────────────────────────

    @Test
    void addRegulationObligation_draft_savesAndReturns() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");

        TermsRegulationObligationEntity saved = new TermsRegulationObligationEntity();
        saved.setId(UUID.randomUUID());
        saved.setPolicySetId(set.getId());
        saved.setRegulationName("GDPR");
        saved.setObligation("Conduct DPIA");
        saved.setOdrlDuty("conductDPIA");

        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));
        when(regObligRepo.save(any())).thenReturn(saved);

        TermsRegulationObligationResponse result = service.addRegulationObligation(set.getId(),
            new RegulationObligationRequest("GDPR", "Conduct DPIA", "conductDPIA"));

        assertThat(result.regulationName()).isEqualTo("GDPR");
        assertThat(result.odrlDuty()).isEqualTo("conductDPIA");
    }

    @Test
    void deleteRegulationObligation_nonDraft_throws409() {
        TermsPolicySetEntity set = policySet("Active", "ACTIVE");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));

        assertThatThrownBy(() -> service.deleteRegulationObligation(set.getId(), UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class);
    }

    // ── listPolicySets ────────────────────────────────────────────────────────

    @Test
    void listPolicySets_returnsMappedResponses() {
        TermsPolicySetEntity s1 = policySet("Policy A", "ACTIVE");
        TermsPolicySetEntity s2 = policySet("Policy B", "DRAFT");
        when(policySetRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(s1, s2));

        List<TermsPolicySetResponse> result = service.listPolicySets();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Policy A");
        assertThat(result.get(1).name()).isEqualTo("Policy B");
    }

    // ── getPolicySet ──────────────────────────────────────────────────────────

    @Test
    void getPolicySet_found_returnsDetailWithChildRules() {
        TermsPolicySetEntity set = policySet("Detail Policy", "DRAFT");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));
        when(classRuleRepo.findByPolicySetId(set.getId())).thenReturn(List.of());
        when(regRuleRepo.findByPolicySetId(set.getId())).thenReturn(List.of());
        when(regObligRepo.findByPolicySetId(set.getId())).thenReturn(List.of());

        TermsPolicyDetailResponse result = service.getPolicySet(set.getId());

        assertThat(result.name()).isEqualTo("Detail Policy");
        assertThat(result.classificationRules()).isEmpty();
        assertThat(result.regulationRules()).isEmpty();
    }

    // ── deleteClassificationRule ──────────────────────────────────────────────

    @Test
    void deleteClassificationRule_draft_deletesRule() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));

        service.deleteClassificationRule(set.getId(), "PUBLIC");

        verify(classRuleRepo).deleteByPolicySetIdAndClassification(set.getId(), "PUBLIC");
    }

    // ── updateRegulationRule ──────────────────────────────────────────────────

    @Test
    void updateRegulationRule_found_updatesAndReturns() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");
        TermsRegulationRuleEntity rule = new TermsRegulationRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setPolicySetId(set.getId());
        rule.setSignalType("KEYWORD");
        rule.setPattern("gdpr");
        rule.setRegulationName("GDPR");
        rule.setSignalLabel("GDPR keyword");

        TermsRegulationRuleEntity saved = new TermsRegulationRuleEntity();
        saved.setId(rule.getId());
        saved.setPolicySetId(set.getId());
        saved.setSignalType("KEYWORD");
        saved.setPattern("pci");
        saved.setRegulationName("PCI-DSS");
        saved.setSignalLabel("PCI keyword");

        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));
        when(regRuleRepo.findById(rule.getId())).thenReturn(Optional.of(rule));
        when(regRuleRepo.save(any())).thenReturn(saved);

        TermsRegulationRuleResponse result = service.updateRegulationRule(set.getId(), rule.getId(),
            new RegulationRuleRequest("KEYWORD", "pci", "PCI-DSS", "PCI keyword"));

        assertThat(rule.getPattern()).isEqualTo("pci");
        assertThat(rule.getRegulationName()).isEqualTo("PCI-DSS");
        assertThat(result.regulationName()).isEqualTo("PCI-DSS");
    }

    // ── deleteRegulationRule ──────────────────────────────────────────────────

    @Test
    void deleteRegulationRule_draft_deletesById() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");
        UUID ruleId = UUID.randomUUID();
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));

        service.deleteRegulationRule(set.getId(), ruleId);

        verify(regRuleRepo).deleteById(ruleId);
    }

    // ── deleteRegulationObligation (happy path) ───────────────────────────────

    @Test
    void deleteRegulationObligation_draft_deletesById() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");
        UUID oblId = UUID.randomUUID();
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));

        service.deleteRegulationObligation(set.getId(), oblId);

        verify(regObligRepo).deleteById(oblId);
    }

    // ── parseList (exception path) ────────────────────────────────────────────

    @Test
    void getActivePolicy_classificationRuleWithInvalidJson_returnsEmptyListForThatField() {
        TermsPolicySetEntity set = policySet("Default", "ACTIVE");

        TermsClassificationRuleEntity rule = new TermsClassificationRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setPolicySetId(set.getId());
        rule.setClassification("PUBLIC");
        rule.setRank(0);
        rule.setAccessLevel("OPEN");
        // Deliberately invalid JSON → parseList catch block fires
        rule.setPermissions("{not-valid-json");
        rule.setProhibitions("[]");
        rule.setObligations("[]");
        rule.setOdrlPermissions("[]");
        rule.setOdrlProhibitions("[]");
        rule.setOdrlDuties("[]");

        when(policySetRepo.findFirstByStatus("ACTIVE")).thenReturn(Optional.of(set));
        when(classRuleRepo.findByPolicySetId(set.getId())).thenReturn(List.of(rule));
        when(regRuleRepo.findByPolicySetId(set.getId())).thenReturn(List.of());
        when(regObligRepo.findByPolicySetId(set.getId())).thenReturn(List.of());

        ActiveTermsPolicy policy = service.getActivePolicy();

        // Should not throw; the bad field returns empty list from catch
        assertThat(policy.classificationRules().get("PUBLIC").permissions()).isEmpty();
    }

    // ── parseList — null and blank inputs ────────────────────────────────────

    @Test
    void getActivePolicy_classificationRuleWithNullPermissions_returnsEmptyList() {
        TermsPolicySetEntity set = policySet("Default", "ACTIVE");
        TermsClassificationRuleEntity rule = new TermsClassificationRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setPolicySetId(set.getId());
        rule.setClassification("INTERNAL");
        rule.setRank(1);
        rule.setAccessLevel("RESTRICTED");
        rule.setPermissions(null);
        rule.setProhibitions("[]");
        rule.setObligations("[]");
        rule.setOdrlPermissions("[]");
        rule.setOdrlProhibitions("[]");
        rule.setOdrlDuties("[]");

        when(policySetRepo.findFirstByStatus("ACTIVE")).thenReturn(Optional.of(set));
        when(classRuleRepo.findByPolicySetId(set.getId())).thenReturn(List.of(rule));
        when(regRuleRepo.findByPolicySetId(set.getId())).thenReturn(List.of());
        when(regObligRepo.findByPolicySetId(set.getId())).thenReturn(List.of());

        ActiveTermsPolicy policy = service.getActivePolicy();

        assertThat(policy.classificationRules().get("INTERNAL").permissions()).isEmpty();
    }

    @Test
    void getActivePolicy_classificationRuleWithBlankPermissions_returnsEmptyList() {
        TermsPolicySetEntity set = policySet("Default", "ACTIVE");
        TermsClassificationRuleEntity rule = new TermsClassificationRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setPolicySetId(set.getId());
        rule.setClassification("CONFIDENTIAL");
        rule.setRank(2);
        rule.setAccessLevel("RESTRICTED");
        rule.setPermissions("   ");
        rule.setProhibitions("[]");
        rule.setObligations("[]");
        rule.setOdrlPermissions("[]");
        rule.setOdrlProhibitions("[]");
        rule.setOdrlDuties("[]");

        when(policySetRepo.findFirstByStatus("ACTIVE")).thenReturn(Optional.of(set));
        when(classRuleRepo.findByPolicySetId(set.getId())).thenReturn(List.of(rule));
        when(regRuleRepo.findByPolicySetId(set.getId())).thenReturn(List.of());
        when(regObligRepo.findByPolicySetId(set.getId())).thenReturn(List.of());

        ActiveTermsPolicy policy = service.getActivePolicy();

        assertThat(policy.classificationRules().get("CONFIDENTIAL").permissions()).isEmpty();
    }

    // ── toJson — null list input ──────────────────────────────────────────────

    @Test
    void upsertClassificationRule_nullPermissionsList_handledByToJson() {
        TermsPolicySetEntity set = policySet("Draft", "DRAFT");
        when(policySetRepo.findById(set.getId())).thenReturn(Optional.of(set));
        when(classRuleRepo.findByPolicySetIdAndClassification(set.getId(), "PUBLIC"))
            .thenReturn(Optional.empty());

        TermsClassificationRuleEntity saved = new TermsClassificationRuleEntity();
        saved.setId(UUID.randomUUID());
        saved.setPolicySetId(set.getId());
        saved.setClassification("PUBLIC");
        saved.setRank(0);
        saved.setAccessLevel("OPEN");
        saved.setPermissions("[]");
        saved.setProhibitions("[]");
        saved.setObligations("[]");
        saved.setOdrlPermissions("[]");
        saved.setOdrlProhibitions("[]");
        saved.setOdrlDuties("[]");
        when(classRuleRepo.save(any())).thenReturn(saved);

        // null list → toJson(null) → ternary null-branch → List.of() → "[]"
        TermsClassificationRuleResponse result = service.upsertClassificationRule(set.getId(), "PUBLIC",
            new UpsertClassificationRuleRequest(0, "OPEN", null, null, null, null, null, null));

        assertThat(result.classification()).isEqualTo("PUBLIC");
        ArgumentCaptor<TermsClassificationRuleEntity> captor =
            ArgumentCaptor.forClass(TermsClassificationRuleEntity.class);
        verify(classRuleRepo).save(captor.capture());
        assertThat(captor.getValue().getPermissions()).isEqualTo("[]");
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private TermsPolicySetEntity policySet(String name, String status) {
        TermsPolicySetEntity e = new TermsPolicySetEntity();
        e.setId(UUID.randomUUID());
        e.setName(name);
        e.setStatus(status);
        return e;
    }
}
