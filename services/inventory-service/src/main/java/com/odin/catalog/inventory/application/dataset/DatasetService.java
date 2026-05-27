package com.odin.catalog.inventory.application.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.api.v1.dto.DatasetAuditResponse;
import com.odin.catalog.inventory.api.v1.dto.DatasetRequest;
import com.odin.catalog.inventory.api.v1.dto.DatasetResponse;
import com.odin.catalog.inventory.api.v1.dto.OwnershipProposalResponse;
import com.odin.catalog.inventory.api.v1.dto.PageResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetAuditLogEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.OwnershipProposalEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetAuditLogRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.OwnershipProposalRepository;
import com.odin.catalog.inventory.infrastructure.kafka.CatalogEventProducer;
import com.odin.catalog.shared.auth.filter.ApiKeyAuthenticationFilter.ApiKeyPrincipal;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetAuditLogRepository auditLogRepository;
    private final OwnershipProposalRepository proposalRepository;
    private final CatalogEventProducer eventProducer;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<DatasetResponse> list(UUID catalogId, String sourceUri, Pageable pageable) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        if (sourceUri != null && !sourceUri.isBlank()) {
            return datasetRepository.findBySourceUri(sourceUri).stream()
                .filter(e -> !e.isDeleted())
                .findFirst()
                .map(e -> PageResponse.ofSingle(toResponse(e)))
                .orElse(PageResponse.empty());
        }
        var page = catalogId != null
            ? datasetRepository.findByTenantIdAndCatalogIdAndIsDeletedFalse(tenantId, catalogId, pageable)
            : datasetRepository.findByTenantIdAndIsDeletedFalse(tenantId, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DatasetResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public DatasetResponse create(DatasetRequest request) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        DatasetEntity entity = new DatasetEntity();
        applyRequest(entity, request, tenantId);
        entity = datasetRepository.save(entity);
        eventProducer.publishDatasetChanged("CREATED", entity);
        UserContext uc = currentUser();
        audit(entity.getId(), "CREATED", uc.id(), uc.email(), null, toResponse(entity), tenantId);
        return toResponse(entity);
    }

    @Transactional
    public DatasetResponse update(UUID id, DatasetRequest request) {
        DatasetEntity entity = findOrThrow(id);
        DatasetResponse before = toResponse(entity);
        applyRequest(entity, request, entity.getTenantId());
        entity = datasetRepository.save(entity);
        eventProducer.publishDatasetChanged("UPDATED", entity);
        DatasetResponse after = toResponse(entity);
        UserContext uc = currentUser();
        audit(id, "UPDATED", uc.id(), uc.email(), before, after, entity.getTenantId());
        return after;
    }

    @Transactional
    public void delete(UUID id) {
        DatasetEntity entity = findOrThrow(id);
        DatasetResponse before = toResponse(entity);
        entity.setDeleted(true);
        datasetRepository.save(entity);
        eventProducer.publishDatasetChanged("DELETED", entity);
        UserContext uc = currentUser();
        audit(id, "DELETED", uc.id(), uc.email(), before, null, entity.getTenantId());
    }

    // ── Ownership ────────────────────────────────────────────────────────────

    @Transactional
    public DatasetResponse assignOwner(UUID datasetId, UUID userId) {
        DatasetEntity entity = findOrThrow(datasetId);
        if (entity.getOwnerId() != null) {
            throw new IllegalStateException(
                "Dataset already has an owner; submit a transfer proposal instead");
        }
        entity.setOwnerId(userId);
        entity = datasetRepository.save(entity);
        UserContext uc = currentUser();
        audit(datasetId, "OWNER_ASSIGNED", uc.id(), uc.email(), null, toResponse(entity), entity.getTenantId());
        return toResponse(entity);
    }

    @Transactional
    public OwnershipProposalResponse proposeTransfer(UUID datasetId, UUID proposedOwnerId) {
        DatasetEntity entity = findOrThrow(datasetId);
        UserContext uc = currentUser();
        UUID proposedById = uc.id() != null ? UUID.fromString(uc.id()) : null;

        // Cancel any pre-existing pending proposals
        proposalRepository.findByDatasetIdAndStatus(datasetId, "PENDING")
            .forEach(p -> {
                p.setStatus("REJECTED");
                p.setResolvedAt(OffsetDateTime.now());
            });

        OwnershipProposalEntity proposal = new OwnershipProposalEntity();
        proposal.setDatasetId(datasetId);
        proposal.setProposedOwnerId(proposedOwnerId);
        proposal.setProposedById(proposedById);
        proposal.setTenantId(entity.getTenantId());
        proposal = proposalRepository.save(proposal);

        audit(datasetId, "OWNER_TRANSFER_PROPOSED", uc.id(), uc.email(), null, null, entity.getTenantId());
        return toProposalResponse(proposal);
    }

    @Transactional
    public DatasetResponse approveTransfer(UUID datasetId, UUID proposalId) {
        DatasetEntity entity = findOrThrow(datasetId);
        UserContext uc = currentUser();
        UUID callerId = uc.id() != null ? UUID.fromString(uc.id()) : null;

        // Only the current owner can approve (system/admin bypasses this check)
        if (entity.getOwnerId() != null && callerId != null
                && !entity.getOwnerId().equals(callerId)
                && !"system".equals(uc.id())) {
            throw new IllegalArgumentException("Only the current owner can approve a transfer proposal");
        }

        OwnershipProposalEntity proposal = proposalRepository.findByIdAndDatasetId(proposalId, datasetId)
            .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));

        if (!"PENDING".equals(proposal.getStatus())) {
            throw new IllegalStateException("Proposal is no longer pending");
        }

        proposal.setStatus("APPROVED");
        proposal.setResolvedAt(OffsetDateTime.now());
        proposalRepository.save(proposal);

        entity.setOwnerId(proposal.getProposedOwnerId());
        entity = datasetRepository.save(entity);

        audit(datasetId, "OWNER_TRANSFER_APPROVED", uc.id(), uc.email(), null, toResponse(entity), entity.getTenantId());
        return toResponse(entity);
    }

    @Transactional
    public OwnershipProposalResponse rejectTransfer(UUID datasetId, UUID proposalId) {
        DatasetEntity entity = findOrThrow(datasetId);
        UserContext uc = currentUser();
        UUID callerId = uc.id() != null ? UUID.fromString(uc.id()) : null;

        if (entity.getOwnerId() != null && callerId != null
                && !entity.getOwnerId().equals(callerId)
                && !"system".equals(uc.id())) {
            throw new IllegalArgumentException("Only the current owner can reject a transfer proposal");
        }

        OwnershipProposalEntity proposal = proposalRepository.findByIdAndDatasetId(proposalId, datasetId)
            .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));

        if (!"PENDING".equals(proposal.getStatus())) {
            throw new IllegalStateException("Proposal is no longer pending");
        }

        proposal.setStatus("REJECTED");
        proposal.setResolvedAt(OffsetDateTime.now());
        proposalRepository.save(proposal);

        audit(datasetId, "OWNER_TRANSFER_REJECTED", uc.id(), uc.email(), null, null, entity.getTenantId());
        return toProposalResponse(proposal);
    }

    @Transactional(readOnly = true)
    public Optional<OwnershipProposalResponse> getPendingProposal(UUID datasetId) {
        findOrThrow(datasetId);
        return proposalRepository.findByDatasetIdAndStatus(datasetId, "PENDING")
            .stream().findFirst()
            .map(this::toProposalResponse);
    }

    // ── Audit history ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<DatasetAuditResponse> getHistory(UUID datasetId, Pageable pageable) {
        findOrThrow(datasetId);
        var page = auditLogRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId, pageable);
        return PageResponse.of(page.map(this::toAuditResponse));
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void audit(UUID datasetId, String eventType, String changedById, String changedByEmail,
                       DatasetResponse before, DatasetResponse after, UUID tenantId) {
        DatasetAuditLogEntity log = new DatasetAuditLogEntity();
        log.setDatasetId(datasetId);
        log.setEventType(eventType);
        log.setChangedById(changedById);
        log.setChangedByEmail(changedByEmail);
        log.setPayloadBefore(toJson(before));
        log.setPayloadAfter(toJson(after));
        log.setTenantId(tenantId);
        auditLogRepository.save(log);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private record UserContext(String id, String email) {}

    private UserContext currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return new UserContext(null, null);

        Object principal = auth.getPrincipal();
        if (principal instanceof ApiKeyPrincipal akp) {
            return new UserContext(akp.ownerId(), null);
        }
        if (principal instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            return new UserContext(sub, email);
        }
        return new UserContext(auth.getName(), null);
    }

    private void applyRequest(DatasetEntity entity, DatasetRequest req, UUID tenantId) {
        entity.setTenantId(tenantId);
        entity.setTitle(req.title());
        entity.setDescription(req.description());
        entity.setCatalogId(req.catalogId());
        entity.setDomainId(req.domainId());
        entity.setAccrualPeriodicity(req.accrualPeriodicity());
        entity.setKeywords(req.keywords());
        entity.setThemes(req.themes());
        entity.setLanguage(req.language());
        entity.setLicense(req.license());
        entity.setVersion(req.version());
        entity.setSourceUri(req.sourceUri());
    }

    private DatasetEntity findOrThrow(UUID id) {
        return datasetRepository.findById(id)
            .filter(e -> !e.isDeleted())
            .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + id));
    }

    DatasetResponse toResponse(DatasetEntity e) {
        return new DatasetResponse(
            e.getId(), e.getTitle(), e.getDescription(),
            e.getCatalogId(), e.getDomainId(), e.getTenantId(),
            e.getAccrualPeriodicity(), e.getKeywords(), e.getThemes(),
            e.getLanguage(), e.getLicense(), e.getVersion(),
            e.getSourceUri(), e.isDeleted(),
            e.getCreatedAt(), e.getUpdatedAt(),
            e.getOwnerId(), null
        );
    }

    private OwnershipProposalResponse toProposalResponse(OwnershipProposalEntity e) {
        return new OwnershipProposalResponse(
            e.getId(), e.getDatasetId(), e.getProposedOwnerId(),
            e.getProposedById(), e.getStatus(), e.getCreatedAt(), e.getResolvedAt()
        );
    }

    private DatasetAuditResponse toAuditResponse(DatasetAuditLogEntity e) {
        return new DatasetAuditResponse(
            e.getId(), e.getDatasetId(), e.getEventType(),
            e.getChangedById(), e.getChangedByEmail(),
            e.getPayloadBefore(), e.getPayloadAfter(),
            e.getCreatedAt()
        );
    }

    private List<OwnershipProposalResponse> toProposalResponseList(List<OwnershipProposalEntity> entities) {
        return entities.stream().map(this::toProposalResponse).toList();
    }
}
