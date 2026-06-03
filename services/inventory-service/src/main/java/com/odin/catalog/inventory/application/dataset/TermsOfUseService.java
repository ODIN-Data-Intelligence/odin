package com.odin.catalog.inventory.application.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.api.v1.dto.TermsOfUseResponse;
import com.odin.catalog.inventory.api.v1.dto.TermsOfUseResponse.DerivationDetails;
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
    private final ObjectMapper objectMapper;

    private static final Map<String, Integer> CLASSIFICATION_RANK = Map.of(
        "PUBLIC", 0, "INTERNAL", 1, "CONFIDENTIAL", 2, "HIGH_CONFIDENTIAL", 3
    );

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

        // Derive if not already explicit; serialize ODRL to hasPolicy
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
        // Re-derive after saving to return explicit policySource
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
        // Readiness counts — always computed so the UI can gate Accept Policy correctly
        int totalPublished      = (int) elementRepository.countPublishedByDatasetId(datasetId);
        int classifiedPublished = (int) elementRepository.countClassifiedPublishedByDatasetId(datasetId);
        int withVocab           = (int) vocabMappingRepository.countPublishedElementsWithVocabByDatasetId(datasetId);

        // Explicit override
        if (dataset.getHasPolicy() != null && !dataset.getHasPolicy().isBlank()) {
            try {
                Map<String, Object> policy = objectMapper.readValue(
                    dataset.getHasPolicy(), new TypeReference<>() {});
                // Still include derivation details so the UI can show element coverage
                DerivationDetails details = new DerivationDetails(
                    totalPublished, classifiedPublished, withVocab, List.of(), 0, List.of(),
                    totalPublished > 0 && classifiedPublished == totalPublished && withVocab == totalPublished
                );
                return new TermsOfUseResponse(null, null, null, null, null, null, policy, "explicit", details);
            } catch (Exception e) {
                log.warn("Failed to parse explicit hasPolicy for dataset {}: {}", datasetId, e.getMessage());
            }
        }

        // Classify
        List<String> allClassifications = elementRepository.findClassificationsByDatasetId(datasetId);
        List<String> validClassifications = allClassifications.stream()
            .filter(CLASSIFICATION_RANK::containsKey)
            .collect(Collectors.toList());
        List<String> distinctClassifications = validClassifications.stream().distinct().sorted().collect(Collectors.toList());

        String effective = validClassifications.stream()
            .max((a, b) -> CLASSIFICATION_RANK.get(a) - CLASSIFICATION_RANK.get(b))
            .orElse(null);

        // Fallback
        if (effective == null) {
            return buildFallback(dataset, datasetId, distinctClassifications,
                totalPublished, classifiedPublished, withVocab);
        }

        // Regulations + signals
        List<String> conceptIris = vocabMappingRepository.findConceptIrisByDatasetId(datasetId);
        RegulationResult regResult = deriveRegulations(conceptIris, dataset.getKeywords());

        // Build rules
        List<String> permissions  = new ArrayList<>(permissionsFor(effective));
        List<String> prohibitions = new ArrayList<>(prohibitionsFor(effective));
        List<String> obligations  = new ArrayList<>(obligationsFor(effective));

        if (regResult.regulations.contains("Market Data Licensing")) {
            obligations.add("Comply with market data vendor licence terms");
        }

        Map<String, Object> odrlPolicy = buildOdrlPolicy(datasetId, effective, prohibitions, obligations);

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
            effective, accessLevelFor(effective),
            permissions, prohibitions, obligations,
            regResult.regulations, odrlPolicy, "derived", details
        );
    }

    // ── Rules by classification ───────────────────────────────────────────────

    private static String accessLevelFor(String c) {
        return switch (c) {
            case "PUBLIC"            -> "OPEN";
            case "INTERNAL"          -> "INTERNAL_ONLY";
            case "CONFIDENTIAL"      -> "RESTRICTED";
            case "HIGH_CONFIDENTIAL" -> "HIGHLY_RESTRICTED";
            default                  -> "INTERNAL_ONLY";
        };
    }

    private static List<String> permissionsFor(String c) {
        return switch (c) {
            case "PUBLIC" -> List.of(
                "Use and reproduce freely",
                "Redistribute with attribution",
                "Incorporate into analytics and products"
            );
            case "INTERNAL" -> List.of(
                "Use for internal analytics and reporting",
                "Share within the organisation"
            );
            case "CONFIDENTIAL" -> List.of(
                "Use for approved internal analytics",
                "Include in regulatory reporting submissions"
            );
            case "HIGH_CONFIDENTIAL" -> List.of(
                "Use only with explicit written approval from the data owner",
                "Access limited to named authorised personnel"
            );
            default -> List.of();
        };
    }

    private static List<String> prohibitionsFor(String c) {
        return switch (c) {
            case "PUBLIC"            -> List.of();
            case "INTERNAL"          -> List.of("Redistribute to external parties", "Sell or sublicense data", "Public disclosure");
            case "CONFIDENTIAL"      -> List.of("Redistribute or share externally", "Reproduce or publish data samples", "Use for commercial purposes");
            case "HIGH_CONFIDENTIAL" -> List.of("Redistribute, reproduce, or present externally", "Incorporate into derived data products", "Use for any non-approved purpose");
            default                  -> List.of();
        };
    }

    private static List<String> obligationsFor(String c) {
        return switch (c) {
            case "PUBLIC"            -> List.of("Cite the data source when publishing results");
            case "INTERNAL"          -> List.of();
            case "CONFIDENTIAL"      -> List.of("Notify the data owner before use in AI/ML models", "Document the intended usage purpose");
            case "HIGH_CONFIDENTIAL" -> List.of("Obtain explicit consent from the data owner prior to access", "Maintain an audit trail of all access and usage", "Report usage to the data governance team quarterly");
            default                  -> List.of();
        };
    }

    // ── Regulation derivation ─────────────────────────────────────────────────

    private record RegulationResult(List<String> regulations, List<String> signals) {}

    private static RegulationResult deriveRegulations(List<String> conceptIris, List<String> keywords) {
        Set<String> regulations = new LinkedHashSet<>();
        Set<String> signals = new LinkedHashSet<>();

        for (String iri : conceptIris) {
            if (iri.contains("fibo-fbc") || iri.contains("fibo-sec")) {
                regulations.add("Securities & Market Regulation");
                signals.add(iri.contains("fibo-fbc") ? "fibo-fbc" : "fibo-sec");
            }
            if (iri.contains("fibo-md"))  { regulations.add("Market Data Licensing"); signals.add("fibo-md"); }
            if (iri.contains("fibo-fnd")) { regulations.add("Financial Foundations Standards"); signals.add("fibo-fnd"); }
        }

        if (keywords != null) {
            for (String kw : keywords) {
                String k = kw.toLowerCase();
                if (k.contains("mifid"))  { regulations.add("MiFID II Transaction Reporting"); signals.add("mifid"); }
                if (k.contains("emir"))   { regulations.add("EMIR Derivatives Reporting"); signals.add("emir"); }
                if (k.contains("finrep")) { regulations.add("EBA FinRep Reporting"); signals.add("finrep"); }
                if (k.contains("gdpr"))   { regulations.add("GDPR Data Protection"); signals.add("gdpr"); }
                if (k.contains("basel"))  { regulations.add("Basel III Capital Requirements"); signals.add("basel"); }
            }
        }

        return new RegulationResult(new ArrayList<>(regulations), new ArrayList<>(signals));
    }

    // ── ODRL JSON builder ─────────────────────────────────────────────────────

    private static Map<String, Object> buildOdrlPolicy(
            UUID datasetId, String classification,
            List<String> prohibitions, List<String> obligations) {

        String target = "dataset:" + datasetId;
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@context", "http://www.w3.org/ns/odrl.jsonld");
        policy.put("@type", "Set");
        policy.put("uid", "https://catalog/datasets/" + datasetId + "/policy");

        List<Map<String, Object>> permList = new ArrayList<>();
        for (String action : odrlPermissionActions(classification)) {
            permList.add(Map.of("target", target, "action", action));
        }
        if (!permList.isEmpty()) policy.put("permission", permList);

        List<Map<String, Object>> prohibList = new ArrayList<>();
        for (String action : odrlProhibitionActions(classification)) {
            prohibList.add(Map.of("target", target, "action", action));
        }
        if (!prohibList.isEmpty()) policy.put("prohibition", prohibList);

        List<Map<String, Object>> dutyList = new ArrayList<>();
        for (String action : odrlDutyActions(classification, obligations)) {
            dutyList.add(Map.of("action", action));
        }
        if (!dutyList.isEmpty()) policy.put("obligation", dutyList);

        return policy;
    }

    private static List<String> odrlPermissionActions(String c) {
        return switch (c) {
            case "PUBLIC"            -> List.of("use", "reproduce", "distribute");
            case "INTERNAL"          -> List.of("use", "present");
            case "CONFIDENTIAL"      -> List.of("use");
            case "HIGH_CONFIDENTIAL" -> List.of("use");
            default                  -> List.of("use");
        };
    }

    private static List<String> odrlProhibitionActions(String c) {
        return switch (c) {
            case "PUBLIC"            -> List.of();
            case "INTERNAL"          -> List.of("distribute", "sell");
            case "CONFIDENTIAL"      -> List.of("distribute", "reproduce", "present");
            case "HIGH_CONFIDENTIAL" -> List.of("distribute", "reproduce", "present", "modify");
            default                  -> List.of();
        };
    }

    private static List<String> odrlDutyActions(String c, List<String> obligations) {
        List<String> actions = new ArrayList<>();
        switch (c) {
            case "PUBLIC"            -> actions.add("attribute");
            case "CONFIDENTIAL"      -> actions.add("notify");
            case "HIGH_CONFIDENTIAL" -> { actions.add("obtainConsent"); actions.add("notify"); }
        }
        if (obligations.stream().anyMatch(o -> o.toLowerCase().contains("market data")))
            actions.add("licenseMarketData");
        return actions;
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
