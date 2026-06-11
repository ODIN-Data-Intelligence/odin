package com.odin.catalog.inventory.application.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.api.v1.dto.TermsOfUseResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.LogicalDataElementRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.VocabMappingRepository;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TermsOfUseServiceTest {

    @Mock DatasetRepository              datasetRepository;
    @Mock LogicalDataElementRepository   elementRepository;
    @Mock VocabMappingRepository         vocabMappingRepository;
    @Mock TermsPolicyService             termsPolicyService;
    @Mock CatalogEventProducer           eventProducer;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks TermsOfUseService service;

    static final UUID DATASET_ID = UUID.randomUUID();

    @BeforeEach
    void defaultCounters() {
        // lenient: early-exit tests (not-found, soft-deleted, explicit-policy) don't reach these stubs
        lenient().when(elementRepository.countPublishedByDatasetId(DATASET_ID)).thenReturn(2L);
        lenient().when(elementRepository.countClassifiedPublishedByDatasetId(DATASET_ID)).thenReturn(2L);
        lenient().when(vocabMappingRepository.countPublishedElementsWithVocabByDatasetId(DATASET_ID)).thenReturn(2L);
        lenient().when(vocabMappingRepository.findConceptIrisByDatasetId(DATASET_ID)).thenReturn(List.of());
    }

    // ── derive: dataset not found ─────────────────────────────────────────────

    @Test
    void derive_datasetNotFound_throws() {
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.derive(DATASET_ID))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void derive_softDeletedDataset_throws() {
        DatasetEntity ds = dataset();
        ds.setDeleted(true);
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));

        assertThatThrownBy(() -> service.derive(DATASET_ID))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── derive: explicit hasPolicy ────────────────────────────────────────────

    @Test
    void derive_explicitHasPolicy_returnsExplicitMode() throws Exception {
        String odrlJson = "{\"@context\":\"http://www.w3.org/ns/odrl.jsonld\",\"@type\":\"Set\"}";
        DatasetEntity ds = dataset();
        ds.setHasPolicy(odrlJson);

        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.policySource()).isEqualTo("explicit");
        assertThat(result.odrlPolicy()).isNotNull();
        verifyNoInteractions(termsPolicyService);
    }

    // ── derive: classification rules ──────────────────────────────────────────

    @Test
    void derive_publicClassification_returnsOpenAccessLevel() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("PUBLIC"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithClassifications());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.policySource()).isEqualTo("derived");
        assertThat(result.effectiveClassification()).isEqualTo("PUBLIC");
        assertThat(result.accessLevel()).isEqualTo("OPEN");
        assertThat(result.permissions()).contains("Read and use data freely");
    }

    @Test
    void derive_confidentialClassification_returnsRestrictedAccessLevel() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("CONFIDENTIAL"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithClassifications());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.effectiveClassification()).isEqualTo("CONFIDENTIAL");
        assertThat(result.accessLevel()).isEqualTo("RESTRICTED");
    }

    @Test
    void derive_mixedClassifications_usesMostRestrictiveByRank() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        // dataset has both PUBLIC (rank=0) and HIGH_CONFIDENTIAL (rank=3) elements
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID))
            .thenReturn(List.of("PUBLIC", "HIGH_CONFIDENTIAL", "PUBLIC"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithClassifications());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.effectiveClassification()).isEqualTo("HIGH_CONFIDENTIAL");
        assertThat(result.accessLevel()).isEqualTo("HIGHLY_RESTRICTED");
    }

    @Test
    void derive_unknownClassificationOnly_fallsBack() {
        DatasetEntity ds = dataset();
        ds.setLicense("Apache 2.0");
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("UNKNOWN_LEVEL"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithClassifications());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.policySource()).isEqualTo("fallback");
    }

    // ── derive: regulation detection ──────────────────────────────────────────

    @Test
    void derive_iriContainsMatch_detectsRegulation() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("PUBLIC"));
        when(vocabMappingRepository.findConceptIrisByDatasetId(DATASET_ID))
            .thenReturn(List.of("https://spec.edmcouncil.org/fibo/ontology/fibo-fbc/FinancialInstruments/concept"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithRegulationRules());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.applicableRegulations()).contains("MiFID II");
        assertThat(result.derivationDetails().matchedSignals()).isNotEmpty();
    }

    @Test
    void derive_keywordMatch_detectsRegulation() {
        DatasetEntity ds = dataset();
        ds.setKeywords(List.of("gdpr compliance", "data subject rights"));
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("INTERNAL"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithRegulationRules());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.applicableRegulations()).contains("GDPR");
    }

    @Test
    void derive_regulationMatchTriggersObligation() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("CONFIDENTIAL"));
        when(vocabMappingRepository.findConceptIrisByDatasetId(DATASET_ID))
            .thenReturn(List.of("https://spec.edmcouncil.org/fibo/ontology/fibo-fbc/something"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithRegulationAndObligation());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.obligations()).contains("File regulatory reports with MiFID authority");
        assertThat(result.odrlPolicy()).containsKey("obligation");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> duties = (List<Map<String, Object>>) result.odrlPolicy().get("obligation");
        assertThat(duties).anyMatch(d -> "filingDuty".equals(d.get("action")));
    }

    @Test
    void derive_noRegulationMatch_noObligationsAdded() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("PUBLIC"));
        // IRIs don't match any pattern
        when(vocabMappingRepository.findConceptIrisByDatasetId(DATASET_ID))
            .thenReturn(List.of("https://example.com/unrelated"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithRegulationAndObligation());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.applicableRegulations()).isEmpty();
        assertThat(result.obligations()).noneMatch(o -> o.contains("regulatory reports"));
    }

    // ── derive: fallback ──────────────────────────────────────────────────────

    @Test
    void derive_noClassifications_fallbackWithLicense() {
        DatasetEntity ds = dataset();
        ds.setLicense("MIT");
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of());
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithClassifications());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.policySource()).isEqualTo("fallback");
        assertThat(result.permissions()).anyMatch(p -> p.contains("MIT"));
    }

    @Test
    void derive_hasPiiElements_detectsPiiRegulation() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("PUBLIC"));
        when(elementRepository.countPiiElementsByDatasetId(DATASET_ID)).thenReturn(1L);
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithHasPiiRule());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.applicableRegulations()).contains("GDPR");
    }

    @Test
    void derive_fallbackWithAccessRights_usesAccessRightsNote() {
        DatasetEntity ds = dataset();
        ds.setAccessRights("Internal Use Only");
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of());
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithClassifications());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.policySource()).isEqualTo("fallback");
        assertThat(result.permissions()).anyMatch(p -> p.contains("Internal Use Only"));
    }

    @Test
    void derive_classificationWithOdrlDuties_includesObligationInPolicy() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("CLASSIFIED_WITH_DUTY"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithOdrlDuties());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.odrlPolicy()).containsKey("obligation");
    }

    @Test
    void derive_fallbackWithRightsStatement_usesRightsStatementNote() {
        DatasetEntity ds = dataset();
        ds.setRightsStatement("Proprietary — no redistribution");
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of());
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithClassifications());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.policySource()).isEqualTo("fallback");
        assertThat(result.permissions()).anyMatch(p -> p.contains("Proprietary"));
    }

    @Test
    void derive_keywordRuleWithNullKeywords_noRegulationDetected() {
        DatasetEntity ds = dataset();
        // keywords is null — KEYWORD rule should be skipped
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("PUBLIC"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithRegulationRules());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.applicableRegulations()).doesNotContain("GDPR");
    }

    @Test
    void derive_obligationWithNullOdrlDuty_isFilteredFromRegulationPieces() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("CONFIDENTIAL"));
        when(vocabMappingRepository.findConceptIrisByDatasetId(DATASET_ID))
            .thenReturn(List.of("https://spec.edmcouncil.org/fibo/ontology/fibo-fbc/something"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithNullDutyObligation());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        // The obligation is matched by IRI but filtered because odrlDuty is null
        assertThat(result.applicableRegulations()).contains("MiFID II");
        // No ODRL obligation piece should appear since duty was null
        if (result.odrlPolicy() != null) {
            assertThat(result.odrlPolicy()).doesNotContainKey("obligation");
        }
    }

    @Test
    void accept_withExplicitPolicy_nullComponents_storesPolicy() {
        DatasetEntity ds = dataset();
        ds.setHasPolicy("{\"@context\":\"http://www.w3.org/ns/odrl.jsonld\",\"@type\":\"Set\"}");
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(datasetRepository.save(ds)).thenReturn(ds);

        // buildResponse returns components=null for explicit path (passes null payloads to publishDatasetChanged)
        service.accept(DATASET_ID);

        verify(datasetRepository).save(ds);
    }

    @Test
    void derive_noClassificationsNoLicense_fallbackWithEmptyPermissions() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of());
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithClassifications());

        TermsOfUseResponse result = service.derive(DATASET_ID);

        assertThat(result.policySource()).isEqualTo("fallback");
        assertThat(result.permissions()).isEmpty();
    }

    // ── accept ────────────────────────────────────────────────────────────────

    @Test
    void accept_storesOdrlPolicyOnDataset() {
        DatasetEntity ds = dataset();
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("PUBLIC"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithClassifications());
        when(datasetRepository.save(ds)).thenReturn(ds);

        service.accept(DATASET_ID);

        assertThat(ds.getHasPolicy()).isNotNull();
        assertThat(ds.getHasPolicy()).contains("@type");
        verify(datasetRepository).save(ds);
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    void reset_clearsHasPolicy() {
        DatasetEntity ds = dataset();
        ds.setHasPolicy("{\"@type\":\"Set\"}");
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(ds));
        when(elementRepository.findClassificationsByDatasetId(DATASET_ID)).thenReturn(List.of("PUBLIC"));
        when(termsPolicyService.getActivePolicy()).thenReturn(policyWithClassifications());
        when(datasetRepository.save(ds)).thenReturn(ds);

        service.reset(DATASET_ID);

        assertThat(ds.getHasPolicy()).isNull();
        verify(datasetRepository).save(ds);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private DatasetEntity dataset() {
        DatasetEntity ds = new DatasetEntity();
        ds.setId(DATASET_ID);
        ds.setTitle("Test Dataset");
        ds.setCreatedAt(OffsetDateTime.now());
        ds.setUpdatedAt(OffsetDateTime.now());
        return ds;
    }

    private ActiveTermsPolicy policyWithClassifications() {
        Map<String, ActiveTermsPolicy.ClassificationRule> rules = new LinkedHashMap<>();
        rules.put("PUBLIC", new ActiveTermsPolicy.ClassificationRule(
            0, "OPEN",
            List.of("Read and use data freely"), List.of(), List.of(),
            List.of("odrl:read"), List.of(), List.of()
        ));
        rules.put("INTERNAL", new ActiveTermsPolicy.ClassificationRule(
            1, "INTERNAL_ONLY",
            List.of("Internal use only"), List.of("No external sharing"), List.of(),
            List.of("odrl:read"), List.of("odrl:distribute"), List.of()
        ));
        rules.put("CONFIDENTIAL", new ActiveTermsPolicy.ClassificationRule(
            2, "RESTRICTED",
            List.of("Restricted access"), List.of("No redistribution"), List.of("Audit access"),
            List.of(), List.of("odrl:distribute", "odrl:derive"), List.of()
        ));
        rules.put("HIGH_CONFIDENTIAL", new ActiveTermsPolicy.ClassificationRule(
            3, "HIGHLY_RESTRICTED",
            List.of("Strictly controlled"), List.of("No external access"), List.of("CISO approval required"),
            List.of(), List.of("odrl:distribute", "odrl:derive", "odrl:modify"), List.of()
        ));
        return new ActiveTermsPolicy(UUID.randomUUID(), "Default Policy", rules, List.of(), List.of());
    }

    private ActiveTermsPolicy policyWithRegulationRules() {
        var classRules = Map.of(
            "PUBLIC",   new ActiveTermsPolicy.ClassificationRule(0, "OPEN",     List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
            "INTERNAL", new ActiveTermsPolicy.ClassificationRule(1, "INTERNAL", List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
        );
        var regRules = List.of(
            new ActiveTermsPolicy.RegulationDetectionRule("IRI_CONTAINS", "fibo-fbc", "MiFID II", "FIBO Banking IRI"),
            new ActiveTermsPolicy.RegulationDetectionRule("KEYWORD", "gdpr", "GDPR", "GDPR keyword")
        );
        return new ActiveTermsPolicy(UUID.randomUUID(), "Test Policy", classRules, regRules, List.of());
    }

    private ActiveTermsPolicy policyWithHasPiiRule() {
        var classRules = Map.of(
            "PUBLIC", new ActiveTermsPolicy.ClassificationRule(0, "OPEN",
                List.of(), List.of(), List.of(), List.of("odrl:read"), List.of(), List.of())
        );
        var regRules = List.of(
            new ActiveTermsPolicy.RegulationDetectionRule("HAS_PII_ELEMENTS", "", "GDPR", "Contains PII elements")
        );
        return new ActiveTermsPolicy(UUID.randomUUID(), "PII Policy", classRules, regRules, List.of());
    }

    private ActiveTermsPolicy policyWithOdrlDuties() {
        var classRules = Map.of(
            "CLASSIFIED_WITH_DUTY", new ActiveTermsPolicy.ClassificationRule(
                1, "CONTROLLED",
                List.of(), List.of(), List.of(),
                List.of(), List.of("odrl:distribute"), List.of("odrl:attribute")  // non-empty odrlDuties
            )
        );
        return new ActiveTermsPolicy(UUID.randomUUID(), "Duty Policy", classRules, List.of(), List.of());
    }

    private ActiveTermsPolicy policyWithNullDutyObligation() {
        var classRules = Map.of(
            "PUBLIC",       new ActiveTermsPolicy.ClassificationRule(0, "OPEN",       List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
            "CONFIDENTIAL", new ActiveTermsPolicy.ClassificationRule(2, "RESTRICTED", List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
        );
        var regRules = List.of(
            new ActiveTermsPolicy.RegulationDetectionRule("IRI_CONTAINS", "fibo-fbc", "MiFID II", "FIBO IRI")
        );
        // Obligation with null odrlDuty → filtered by buildRegulationPieces
        var obligations = List.of(
            new ActiveTermsPolicy.RegulationObligation("MiFID II", "Some obligation text", null)
        );
        return new ActiveTermsPolicy(UUID.randomUUID(), "Test Policy", classRules, regRules, obligations);
    }

    private ActiveTermsPolicy policyWithRegulationAndObligation() {
        var classRules = Map.of(
            "PUBLIC",      new ActiveTermsPolicy.ClassificationRule(0, "OPEN",       List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
            "CONFIDENTIAL",new ActiveTermsPolicy.ClassificationRule(2, "RESTRICTED", List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
        );
        var regRules = List.of(
            new ActiveTermsPolicy.RegulationDetectionRule("IRI_CONTAINS", "fibo-fbc", "MiFID II", "FIBO IRI")
        );
        var obligations = List.of(
            new ActiveTermsPolicy.RegulationObligation("MiFID II", "File regulatory reports with MiFID authority", "filingDuty")
        );
        return new ActiveTermsPolicy(UUID.randomUUID(), "Test Policy", classRules, regRules, obligations);
    }
}
