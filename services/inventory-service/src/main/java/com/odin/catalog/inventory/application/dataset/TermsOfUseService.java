package com.odin.catalog.inventory.application.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.api.v1.dto.TermsOfUseResponse;
import com.odin.catalog.inventory.api.v1.dto.TermsOfUseResponse.DerivationDetails;
import com.odin.catalog.inventory.application.dataset.ActiveTermsPolicy.ClassificationRule;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.LogicalDataElementRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.VocabMappingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TermsOfUseService {

    private static final Logger log = LoggerFactory.getLogger(TermsOfUseService.class);

    private final DatasetRepository datasetRepository;
    private final LogicalDataElementRepository elementRepository;
    private final VocabMappingRepository vocabMappingRepository;
    private final TermsPolicyService termsPolicyService;
    private final ObjectMapper objectMapper;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TermsOfUseResponse derive(UUID datasetId) {
        DatasetEntity dataset = findOrThrow(datasetId);
        return buildResponse(dataset, datasetId);
    }

    @Transactional
    public TermsOfUseResponse accept(UUID datasetId) {
        DatasetEntity dataset = findOrThrow(datasetId);
        TermsOfUseResponse derived = buildResponse(dataset, datasetId);

        Map<String, Object> policy = derived.odrlPolicy();
        if (policy != null) {
            try {
                dataset.setHasPolicy(objectMapper.writeValueAsString(policy));
                datasetRepository.save(dataset);
                log.info("action=TERMS_ACCEPTED datasetId={}", datasetId);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize ODRL policy: " + e.getMessage(), e);
            }
        }
        return buildResponse(dataset, datasetId);
    }

    @Transactional
    public TermsOfUseResponse reset(UUID datasetId) {
        DatasetEntity dataset = findOrThrow(datasetId);
        dataset.setHasPolicy(null);
        datasetRepository.save(dataset);
        log.info("action=TERMS_RESET datasetId={}", datasetId);
        return buildResponse(dataset, datasetId);
    }

    // ── Core derivation ───────────────────────────────────────────────────────

    private TermsOfUseResponse buildResponse(DatasetEntity dataset, UUID datasetId) {
        int totalPublished      = (int) elementRepository.countPublishedByDatasetId(datasetId);
        int classifiedPublished = (int) elementRepository.countClassifiedPublishedByDatasetId(datasetId);
        int withVocab           = (int) vocabMappingRepository.countPublishedElementsWithVocabByDatasetId(datasetId);

        // Explicit override
        if (dataset.getHasPolicy() != null && !dataset.getHasPolicy().isBlank()) {
            try {
                Map<String, Object> policy = objectMapper.readValue(
                    dataset.getHasPolicy(), new TypeReference<>() {});
                DerivationDetails details = new DerivationDetails(
                    totalPublished, classifiedPublished, withVocab, List.of(), 0, List.of(),
                    totalPublished > 0 && classifiedPublished == totalPublished && withVocab == totalPublished
                );
                return new TermsOfUseResponse(null, null, null, null, null, null, policy, "explicit", details);
            } catch (Exception e) {
                log.warn("Failed to parse explicit hasPolicy for dataset {}: {}", datasetId, e.getMessage());
            }
        }

        ActiveTermsPolicy policy = termsPolicyService.getActivePolicy();

        // Classify — find most restrictive classification by rank
        List<String> allClassifications = elementRepository.findClassificationsByDatasetId(datasetId);
        List<String> validClassifications = allClassifications.stream()
            .filter(c -> policy.classificationRules().containsKey(c))
            .collect(Collectors.toList());
        List<String> distinctClassifications = validClassifications.stream().distinct().sorted().collect(Collectors.toList());

        String effective = validClassifications.stream()
            .max((a, b) -> policy.classificationRules().get(a).rank() - policy.classificationRules().get(b).rank())
            .orElse(null);

        if (effective == null) {
            return buildFallback(dataset, datasetId, distinctClassifications,
                totalPublished, classifiedPublished, withVocab);
        }

        ClassificationRule rule = policy.classificationRules().get(effective);

        // Detect regulations via policy rules
        List<String> conceptIris = vocabMappingRepository.findConceptIrisByDatasetId(datasetId);
        RegulationResult regResult = detectRegulations(conceptIris, dataset.getKeywords(), policy);

        // Build rules from policy
        List<String> permissions  = new ArrayList<>(rule.permissions());
        List<String> prohibitions = new ArrayList<>(rule.prohibitions());
        List<String> obligations  = new ArrayList<>(rule.obligations());

        // Add regulation-triggered obligations
        List<String> additionalOdrlDuties = new ArrayList<>();
        for (ActiveTermsPolicy.RegulationObligation ro : policy.regulationObligations()) {
            if (regResult.regulations.contains(ro.regulationName())) {
                obligations.add(ro.obligation());
                if (ro.odrlDuty() != null) additionalOdrlDuties.add(ro.odrlDuty());
            }
        }

        Map<String, Object> odrlPolicy = buildOdrlPolicy(datasetId, rule, prohibitions, additionalOdrlDuties);

        boolean readyToAccept = totalPublished > 0
            && classifiedPublished == totalPublished
            && withVocab == totalPublished;

        DerivationDetails details = new DerivationDetails(
            totalPublished, classifiedPublished, withVocab,
            distinctClassifications, conceptIris.size(),
            regResult.signals, readyToAccept
        );

        log.debug("terms.derived datasetId={} classification={} regulations={}", datasetId, effective, regResult.regulations);

        return new TermsOfUseResponse(
            effective, rule.accessLevel(),
            permissions, prohibitions, obligations,
            regResult.regulations, odrlPolicy, "derived", details
        );
    }

    // ── Regulation detection via policy rules ─────────────────────────────────

    private record RegulationResult(List<String> regulations, List<String> signals) {}

    private static RegulationResult detectRegulations(
            List<String> conceptIris, List<String> keywords, ActiveTermsPolicy policy) {
        Set<String> regulations = new LinkedHashSet<>();
        Set<String> signals = new LinkedHashSet<>();

        for (ActiveTermsPolicy.RegulationDetectionRule rule : policy.regulationRules()) {
            switch (rule.signalType()) {
                case "IRI_CONTAINS" -> {
                    for (String iri : conceptIris) {
                        if (iri.contains(rule.pattern())) {
                            regulations.add(rule.regulationName());
                            signals.add(rule.signalLabel());
                            break;
                        }
                    }
                }
                case "KEYWORD" -> {
                    if (keywords != null) {
                        for (String kw : keywords) {
                            if (kw.toLowerCase().contains(rule.pattern())) {
                                regulations.add(rule.regulationName());
                                signals.add(rule.signalLabel());
                                break;
                            }
                        }
                    }
                }
                case "LOGICAL_TYPE" -> {
                    // reserved for element-level logical type matching
                }
            }
        }

        return new RegulationResult(new ArrayList<>(regulations), new ArrayList<>(signals));
    }

    // ── ODRL JSON builder ─────────────────────────────────────────────────────

    private static Map<String, Object> buildOdrlPolicy(
            UUID datasetId, ClassificationRule rule,
            List<String> prohibitions, List<String> additionalOdrlDuties) {

        String target = "dataset:" + datasetId;
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@context", "http://www.w3.org/ns/odrl.jsonld");
        policy.put("@type", "Set");
        policy.put("uid", "https://catalog/datasets/" + datasetId + "/policy");

        List<Map<String, Object>> permList = new ArrayList<>();
        for (String action : rule.odrlPermissions()) {
            permList.add(Map.of("target", target, "action", action));
        }
        if (!permList.isEmpty()) policy.put("permission", permList);

        List<Map<String, Object>> prohibList = new ArrayList<>();
        for (String action : rule.odrlProhibitions()) {
            prohibList.add(Map.of("target", target, "action", action));
        }
        if (!prohibList.isEmpty()) policy.put("prohibition", prohibList);

        List<Map<String, Object>> dutyList = new ArrayList<>();
        for (String action : rule.odrlDuties()) {
            dutyList.add(Map.of("action", action));
        }
        for (String action : additionalOdrlDuties) {
            dutyList.add(Map.of("action", action));
        }
        if (!dutyList.isEmpty()) policy.put("obligation", dutyList);

        return policy;
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private TermsOfUseResponse buildFallback(DatasetEntity dataset, UUID datasetId,
                                              List<String> distinctClassifications,
                                              int totalPublished, int classifiedPublished, int withVocab) {
        String note = null;
        if (dataset.getLicense() != null && !dataset.getLicense().isBlank())
            note = dataset.getLicense();
        else if (dataset.getAccessRights() != null && !dataset.getAccessRights().isBlank())
            note = dataset.getAccessRights();
        else if (dataset.getRightsStatement() != null && !dataset.getRightsStatement().isBlank())
            note = dataset.getRightsStatement();

        List<String> permissions = note != null ? List.of("See declared terms: " + note) : List.of();

        Map<String, Object> odrlPolicy = new LinkedHashMap<>();
        odrlPolicy.put("@context", "http://www.w3.org/ns/odrl.jsonld");
        odrlPolicy.put("@type", "Set");
        odrlPolicy.put("uid", "https://catalog/datasets/" + datasetId + "/policy");
        if (note != null) odrlPolicy.put("dcterms:license", note);

        boolean readyToAccept = totalPublished > 0
            && classifiedPublished == totalPublished
            && withVocab == totalPublished;

        DerivationDetails details = new DerivationDetails(
            totalPublished, classifiedPublished, withVocab,
            distinctClassifications, 0, List.of(), readyToAccept
        );
        return new TermsOfUseResponse(null, null, permissions, List.of(), List.of(),
            List.of(), odrlPolicy, "fallback", details);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private DatasetEntity findOrThrow(UUID datasetId) {
        return datasetRepository.findById(datasetId)
            .filter(e -> !e.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + datasetId));
    }
}
