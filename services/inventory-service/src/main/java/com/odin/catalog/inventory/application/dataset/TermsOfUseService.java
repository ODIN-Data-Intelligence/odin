package com.odin.catalog.inventory.application.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.api.v1.dto.TermsOfUseResponse;
import com.odin.catalog.inventory.api.v1.dto.TermsOfUseResponse.DerivationDetails;
import com.odin.catalog.inventory.api.v1.dto.TermsOfUseResponse.PolicyComponent;
import com.odin.catalog.inventory.application.dataset.ActiveTermsPolicy.ClassificationRule;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.LogicalDataElementRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.VocabMappingRepository;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import com.odin.catalog.shared.models.policy.PolicyComponentPayload;
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
    private final CatalogEventProducer eventProducer;

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

        List<PolicyComponent> components = derived.policyComponents();
        Map<String, Object> policy = derived.odrlPolicy();

        if (policy != null) {
            try {
                dataset.setHasPolicy(objectMapper.writeValueAsString(policy));
                datasetRepository.save(dataset);

                List<PolicyComponentPayload> payloads = components == null ? null :
                    components.stream()
                        .map(c -> new PolicyComponentPayload(
                            c.pieceType(), c.dimensionKey(), c.label(),
                            serializeFragment(c.policyFragment())))
                        .collect(Collectors.toList());

                eventProducer.publishDatasetChanged("UPDATED", dataset, payloads);
                log.info("action=TERMS_ACCEPTED datasetId={} pieces={}", datasetId,
                    payloads == null ? 0 : payloads.size());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize ODRL policy: " + e.getMessage(), e);
            }
        }
        // Return the derived response with source overridden to "explicit" so components are preserved
        return new TermsOfUseResponse(
            derived.effectiveClassification(), derived.accessLevel(),
            derived.permissions(), derived.prohibitions(), derived.obligations(),
            derived.applicableRegulations(), derived.odrlPolicy(),
            "explicit", derived.derivationDetails(), derived.policyComponents());
    }

    @Transactional
    public TermsOfUseResponse reset(UUID datasetId) {
        DatasetEntity dataset = findOrThrow(datasetId);
        dataset.setHasPolicy(null);
        datasetRepository.save(dataset);
        eventProducer.publishDatasetChanged("UPDATED", dataset);
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
                // For explicit, reconstruct components from stored policy — no original pieces available
                return new TermsOfUseResponse(null, null, null, null, null, null, policy, "explicit", details, null);
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
        boolean hasPiiElements = elementRepository.countPiiElementsByDatasetId(datasetId) > 0;
        RegulationResult regResult = detectRegulations(conceptIris, dataset.getKeywords(), hasPiiElements, policy);

        // Build pieces
        List<PolicyComponent> pieces = new ArrayList<>();
        pieces.add(buildClassificationPiece(effective, rule));

        List<ActiveTermsPolicy.RegulationObligation> triggeredObligations = policy.regulationObligations()
            .stream()
            .filter(ro -> regResult.regulations.contains(ro.regulationName()))
            .collect(Collectors.toList());
        pieces.addAll(buildRegulationPieces(triggeredObligations));

        // Assemble monolithic ODRL from pieces
        Map<String, Object> odrlPolicy = assemblePieces(datasetId, pieces);

        // Human-readable lists from classification rule + regulations
        List<String> permissions  = new ArrayList<>(rule.permissions());
        List<String> prohibitions = new ArrayList<>(rule.prohibitions());
        List<String> obligations  = new ArrayList<>(rule.obligations());
        for (ActiveTermsPolicy.RegulationObligation ro : triggeredObligations) {
            obligations.add(ro.obligation());
        }

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
            regResult.regulations, odrlPolicy, "derived", details, pieces
        );
    }

    // ── Piece builders ────────────────────────────────────────────────────────

    private PolicyComponent buildClassificationPiece(String classification, ClassificationRule rule) {
        Map<String, Object> fragment = new LinkedHashMap<>();
        if (!rule.odrlPermissions().isEmpty()) {
            fragment.put("permission", rule.odrlPermissions().stream()
                .map(a -> Map.of("action", a)).collect(Collectors.toList()));
        }
        if (!rule.odrlProhibitions().isEmpty()) {
            fragment.put("prohibition", rule.odrlProhibitions().stream()
                .map(a -> Map.of("action", a)).collect(Collectors.toList()));
        }
        if (!rule.odrlDuties().isEmpty()) {
            fragment.put("obligation", rule.odrlDuties().stream()
                .map(a -> Map.of("action", a)).collect(Collectors.toList()));
        }
        String label = rule.accessLevel() + " — " + classification + " data access rules";
        return new PolicyComponent("CLASSIFICATION", classification, label, fragment);
    }

    private List<PolicyComponent> buildRegulationPieces(
            List<ActiveTermsPolicy.RegulationObligation> obligations) {
        return obligations.stream()
            .filter(ro -> ro.odrlDuty() != null && !ro.odrlDuty().isBlank())
            .map(ro -> {
                Map<String, Object> fragment = Map.of(
                    "obligation", List.of(Map.of("action", ro.odrlDuty())));
                return new PolicyComponent("REGULATION", ro.regulationName(),
                    ro.regulationName(), fragment);
            })
            .collect(Collectors.toList());
    }

    // ── Assembly ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> assemblePieces(UUID datasetId, List<PolicyComponent> pieces) {
        String target = "dataset:" + datasetId;
        List<Map<String, Object>> permissions  = new ArrayList<>();
        List<Map<String, Object>> prohibitions = new ArrayList<>();
        List<Map<String, Object>> obligations  = new ArrayList<>();

        for (PolicyComponent piece : pieces) {
            Map<String, Object> frag = piece.policyFragment();
            if (frag.containsKey("permission")) {
                for (Map<String, Object> r : (List<Map<String, Object>>) frag.get("permission")) {
                    Map<String, Object> rule = new LinkedHashMap<>(r);
                    rule.put("target", target);
                    permissions.add(rule);
                }
            }
            if (frag.containsKey("prohibition")) {
                for (Map<String, Object> r : (List<Map<String, Object>>) frag.get("prohibition")) {
                    Map<String, Object> rule = new LinkedHashMap<>(r);
                    rule.put("target", target);
                    prohibitions.add(rule);
                }
            }
            if (frag.containsKey("obligation")) {
                obligations.addAll((List<Map<String, Object>>) frag.get("obligation"));
            }
        }

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@context", "http://www.w3.org/ns/odrl.jsonld");
        policy.put("@type", "Set");
        policy.put("uid", "https://catalog/datasets/" + datasetId + "/policy");
        if (!permissions.isEmpty())  policy.put("permission",  permissions);
        if (!prohibitions.isEmpty()) policy.put("prohibition", prohibitions);
        if (!obligations.isEmpty())  policy.put("obligation",  obligations);
        return policy;
    }

    // ── Regulation detection via policy rules ─────────────────────────────────

    private record RegulationResult(List<String> regulations, List<String> signals) {}

    private static RegulationResult detectRegulations(
            List<String> conceptIris, List<String> keywords,
            boolean hasPiiElements, ActiveTermsPolicy policy) {
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
                case "HAS_PII_ELEMENTS" -> {
                    if (hasPiiElements) {
                        regulations.add(rule.regulationName());
                        signals.add(rule.signalLabel());
                    }
                }
                case "LOGICAL_TYPE" -> {
                    // reserved for element-level logical type matching
                }
            }
        }

        return new RegulationResult(new ArrayList<>(regulations), new ArrayList<>(signals));
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
            List.of(), odrlPolicy, "fallback", details, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String serializeFragment(Map<String, Object> fragment) {
        try {
            return objectMapper.writeValueAsString(fragment);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize policy fragment", e);
        }
    }

    private DatasetEntity findOrThrow(UUID datasetId) {
        return datasetRepository.findById(datasetId)
            .filter(e -> !e.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + datasetId));
    }
}
