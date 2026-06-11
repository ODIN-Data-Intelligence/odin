package com.odin.catalog.policy.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.policy.api.v1.dto.PolicyComponentSummary;
import com.odin.catalog.policy.api.v1.dto.PolicyComponentsResponse;
import com.odin.catalog.policy.api.v1.dto.PolicyResponse;
import com.odin.catalog.policy.infrastructure.jpa.DatasetPolicyLinkEntity;
import com.odin.catalog.policy.infrastructure.jpa.DatasetPolicyLinkRepository;
import com.odin.catalog.policy.infrastructure.jpa.PolicyPieceEntity;
import com.odin.catalog.policy.infrastructure.jpa.PolicyPieceRepository;
import com.odin.catalog.policy.infrastructure.jpa.PolicyRecordEntity;
import com.odin.catalog.policy.infrastructure.jpa.PolicyRecordRepository;
import com.odin.catalog.shared.models.policy.PolicyComponentPayload;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PolicyRegistryService {

    private static final Logger log = LoggerFactory.getLogger(PolicyRegistryService.class);

    private final PolicyRecordRepository repository;
    private final PolicyPieceRepository pieceRepository;
    private final DatasetPolicyLinkRepository linkRepository;
    private final ObjectMapper objectMapper;

    // ── Policy record CRUD ────────────────────────────────────────────────────

    @Transactional
    public PolicyRecordEntity upsert(UUID datasetId, UUID tenantId, String policyJson, String policyLevel) {
        PolicyRecordEntity entity = repository
            .findByDatasetIdAndTenantId(datasetId, tenantId)
            .orElseGet(PolicyRecordEntity::new);

        boolean isNew = entity.getId() == null;
        entity.setDatasetId(datasetId);
        entity.setTenantId(tenantId);
        entity.setPolicyJson(policyJson);
        entity.setPolicyLevel(policyLevel != null ? policyLevel : "A");
        PolicyRecordEntity saved = repository.save(entity);
        log.info("action=POLICY_UPSERTED datasetId={} tenantId={} level={} created={}",
            datasetId, tenantId, saved.getPolicyLevel(), isNew);
        return saved;
    }

    @Transactional
    public void upsertFromEvent(String datasetId, String tenantId, String policyJson) {
        upsert(UUID.fromString(datasetId), UUID.fromString(tenantId), policyJson, "A");
    }

    public Optional<PolicyRecordEntity> find(UUID datasetId, UUID tenantId) {
        return repository.findByDatasetIdAndTenantId(datasetId, tenantId);
    }

    @Transactional
    public void delete(UUID datasetId, UUID tenantId) {
        repository.deleteByDatasetIdAndTenantId(datasetId, tenantId);
        log.info("action=POLICY_DELETED datasetId={} tenantId={}", datasetId, tenantId);
    }

    // ── Policy pieces ─────────────────────────────────────────────────────────

    @Transactional
    public PolicyPieceEntity upsertPiece(UUID tenantId, String pieceType, String dimensionKey,
                                          String label, String policyJson) {
        PolicyPieceEntity entity = pieceRepository
            .findByTenantIdAndPieceTypeAndDimensionKey(tenantId, pieceType, dimensionKey)
            .orElseGet(PolicyPieceEntity::new);

        entity.setTenantId(tenantId);
        entity.setPieceType(pieceType);
        entity.setDimensionKey(dimensionKey);
        entity.setLabel(label);
        entity.setPolicyJson(policyJson);
        entity.setPolicyLevel("A");
        PolicyPieceEntity saved = pieceRepository.save(entity);
        log.debug("action=PIECE_UPSERTED tenantId={} type={} dimensionKey={} pieceId={}",
            tenantId, pieceType, dimensionKey, saved.getId());
        return saved;
    }

    // ── Dataset policy links ──────────────────────────────────────────────────

    @Transactional
    public void upsertFromComponents(String datasetIdStr, String tenantIdStr,
                                      List<PolicyComponentPayload> components) {
        UUID datasetId = UUID.fromString(datasetIdStr);
        UUID tenantId  = UUID.fromString(tenantIdStr);

        // Upsert each piece and collect
        List<PolicyPieceEntity> pieces = new ArrayList<>();
        for (PolicyComponentPayload c : components) {
            PolicyPieceEntity piece = upsertPiece(
                tenantId, c.pieceType(), c.dimensionKey(), c.label(), c.policyJson());
            pieces.add(piece);
        }

        // Replace links for this dataset
        linkRepository.deleteByDatasetIdAndTenantId(datasetId, tenantId);
        for (PolicyPieceEntity piece : pieces) {
            DatasetPolicyLinkEntity link = new DatasetPolicyLinkEntity();
            link.setDatasetId(datasetId);
            link.setTenantId(tenantId);
            link.setPiece(piece);
            linkRepository.save(link);
        }

        // Assemble and store the composed policy_record
        String assembled = assemblePolicy(datasetId, pieces);
        upsert(datasetId, tenantId, assembled, "A");

        log.info("action=COMPONENTS_SYNCED datasetId={} pieces={}", datasetId, pieces.size());
    }

    @Transactional
    public void deleteLinks(UUID datasetId, UUID tenantId) {
        linkRepository.deleteByDatasetIdAndTenantId(datasetId, tenantId);
        log.debug("action=LINKS_DELETED datasetId={} tenantId={}", datasetId, tenantId);
    }

    public List<DatasetPolicyLinkEntity> findLinks(UUID datasetId, UUID tenantId) {
        return linkRepository.findByDatasetIdAndTenantId(datasetId, tenantId);
    }

    // ── Response assembly ─────────────────────────────────────────────────────

    public PolicyResponse toResponse(PolicyRecordEntity e) {
        Object parsed = null;
        try {
            parsed = objectMapper.readValue(e.getPolicyJson(), Object.class);
        } catch (Exception ignored) {
            parsed = e.getPolicyJson();
        }
        return new PolicyResponse(e.getId(), e.getDatasetId(), e.getTenantId(),
            e.getPolicyLevel(), parsed, e.getCreatedAt(), e.getUpdatedAt());
    }

    public PolicyComponentsResponse getComponents(UUID datasetId, UUID tenantId) {
        PolicyRecordEntity record = find(datasetId, tenantId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "No policy record found for dataset " + datasetId));

        List<DatasetPolicyLinkEntity> links = findLinks(datasetId, tenantId);

        List<PolicyComponentSummary> summaries = links.stream().map(link -> {
            PolicyPieceEntity piece = link.getPiece();
            Object fragment = null;
            try { fragment = objectMapper.readValue(piece.getPolicyJson(), Object.class); }
            catch (Exception ignored) { fragment = piece.getPolicyJson(); }
            return new PolicyComponentSummary(
                piece.getId(), piece.getPieceType(), piece.getDimensionKey(),
                piece.getLabel(), piece.getPolicyLevel(), fragment, link.getAppliedAt());
        }).toList();

        Object assembled = null;
        try { assembled = objectMapper.readValue(record.getPolicyJson(), Object.class); }
        catch (Exception ignored) { assembled = record.getPolicyJson(); }

        return new PolicyComponentsResponse(datasetId, tenantId, summaries, assembled);
    }

    // ── Assembly ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String assemblePolicy(UUID datasetId, List<PolicyPieceEntity> pieces) {
        String target = "dataset:" + datasetId;
        List<Map<String, Object>> permissions  = new ArrayList<>();
        List<Map<String, Object>> prohibitions = new ArrayList<>();
        List<Map<String, Object>> obligations  = new ArrayList<>();

        for (PolicyPieceEntity piece : pieces) {
            try {
                Map<String, Object> frag = objectMapper.readValue(
                    piece.getPolicyJson(), new TypeReference<>() {});

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
            } catch (Exception e) {
                log.warn("Failed to parse piece policyJson for piece={}: {}", piece.getId(), e.getMessage());
            }
        }

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@context", "http://www.w3.org/ns/odrl.jsonld");
        policy.put("@type", "Set");
        policy.put("uid", "https://catalog/datasets/" + datasetId + "/policy");
        if (!permissions.isEmpty())  policy.put("permission",  permissions);
        if (!prohibitions.isEmpty()) policy.put("prohibition", prohibitions);
        if (!obligations.isEmpty())  policy.put("obligation",  obligations);

        try {
            return objectMapper.writeValueAsString(policy);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize assembled policy", e);
        }
    }
}
