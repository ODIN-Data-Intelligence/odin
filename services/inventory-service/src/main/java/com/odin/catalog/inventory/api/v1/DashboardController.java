package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.api.v1.dto.ActivityChangeResponse;
import com.odin.catalog.inventory.api.v1.dto.ActivityProposalResponse;
import com.odin.catalog.inventory.api.v1.dto.DashboardSummaryResponse;
import com.odin.catalog.inventory.api.v1.dto.OwnershipProposalResponse;
import com.odin.catalog.inventory.api.v1.dto.UserActivityResponse;
import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetAuditLogEntity;
import com.odin.catalog.inventory.infrastructure.jpa.entity.OwnershipProposalEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DataProductRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetAuditLogRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.OwnershipProposalRepository;
import com.odin.catalog.shared.auth.filter.ApiKeyAuthenticationFilter.ApiKeyPrincipal;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Tag(name = "Dashboard", description = "Summary statistics and activity feed for the current user")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DatasetRepository datasetRepository;
    private final DataProductRepository dataProductRepository;
    private final OwnershipProposalRepository proposalRepository;
    private final DatasetAuditLogRepository auditLogRepository;

    @Operation(summary = "Get dashboard summary",
        description = "Returns owned dataset/data-product counts and pending incoming ownership transfer requests for the authenticated user.")
    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary() {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        UUID userId = currentUserId();

        if (userId == null) {
            return new DashboardSummaryResponse(0, 0, List.of());
        }

        long datasetCount = datasetRepository.countByOwnerIdAndTenantIdAndIsDeletedFalse(userId, tenantId);
        long dataProductCount = dataProductRepository.countByOwnerIdAndTenantIdAndIsDeletedFalse(userId, tenantId);

        List<OwnershipProposalResponse> pending = proposalRepository
            .findByProposedOwnerIdAndStatusAndTenantId(userId, "PENDING", tenantId)
            .stream()
            .map(p -> new OwnershipProposalResponse(
                p.getId(), p.getDatasetId(), p.getProposedOwnerId(),
                p.getProposedById(), p.getStatus(), p.getCreatedAt(), p.getResolvedAt(),
                p.getNote()))
            .toList();

        return new DashboardSummaryResponse(datasetCount, dataProductCount, pending);
    }

    @Operation(summary = "Get user activity",
        description = "Returns all ownership proposals the user is involved in (as proposer or nominee) and recent dataset changes they made.")
    @GetMapping("/activity")
    public UserActivityResponse getActivity() {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        UUID userId = currentUserId();

        if (userId == null) {
            return new UserActivityResponse(List.of(), List.of());
        }

        // Merge proposals where user is proposer or nominee, deduplicate, sort by date desc
        List<OwnershipProposalEntity> asProposer =
            proposalRepository.findByProposedByIdAndTenantIdOrderByCreatedAtDesc(userId, tenantId);
        List<OwnershipProposalEntity> asNominee =
            proposalRepository.findByProposedOwnerIdAndTenantIdOrderByCreatedAtDesc(userId, tenantId);

        // Deduplicate by id (user may appear as both proposer and nominee on the same proposal)
        Map<UUID, OwnershipProposalEntity> seen = new java.util.LinkedHashMap<>();
        Stream.concat(asProposer.stream(), asNominee.stream())
            .forEach(p -> seen.putIfAbsent(p.getId(), p));
        List<OwnershipProposalEntity> allProposals = new ArrayList<>(seen.values());
        allProposals.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        // Audit entries by this user (most recent 50)
        List<DatasetAuditLogEntity> auditEntries = auditLogRepository
            .findByChangedByIdAndTenantIdOrderByCreatedAtDesc(userId.toString(), tenantId, PageRequest.of(0, 50))
            .getContent();

        // Batch-resolve dataset titles
        List<UUID> datasetIds = Stream.concat(
            allProposals.stream().map(OwnershipProposalEntity::getDatasetId),
            auditEntries.stream().map(DatasetAuditLogEntity::getDatasetId)
        ).distinct().toList();

        Map<UUID, String> titleById = datasetRepository.findAllById(datasetIds).stream()
            .collect(Collectors.toMap(
                d -> d.getId(),
                d -> d.getTitle() != null ? d.getTitle() : d.getId().toString()
            ));

        List<ActivityProposalResponse> proposals = allProposals.stream()
            .map(p -> new ActivityProposalResponse(
                p.getId(),
                p.getDatasetId(),
                titleById.getOrDefault(p.getDatasetId(), p.getDatasetId().toString()),
                p.getProposedOwnerId(),
                p.getProposedById(),
                userId.equals(p.getProposedById()) ? "PROPOSER" : "NOMINEE",
                p.getStatus(),
                p.getCreatedAt(),
                p.getResolvedAt(),
                p.getNote()
            ))
            .toList();

        List<ActivityChangeResponse> changes = auditEntries.stream()
            .map(e -> new ActivityChangeResponse(
                e.getId(),
                e.getDatasetId(),
                titleById.getOrDefault(e.getDatasetId(), e.getDatasetId().toString()),
                e.getEventType(),
                e.getCreatedAt()
            ))
            .toList();

        return new UserActivityResponse(proposals, changes);
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof ApiKeyPrincipal akp && akp.ownerId() != null) {
            try { return UUID.fromString(akp.ownerId()); } catch (IllegalArgumentException e) { return null; }
        }
        if (principal instanceof Jwt jwt && jwt.getSubject() != null) {
            try { return UUID.fromString(jwt.getSubject()); } catch (IllegalArgumentException e) { return null; }
        }
        return null;
    }
}
