package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.api.v1.dto.AssignOwnerRequest;
import com.odin.catalog.inventory.api.v1.dto.DatasetAuditResponse;
import com.odin.catalog.inventory.api.v1.dto.DatasetRequest;
import com.odin.catalog.inventory.api.v1.dto.LogicalElementAuditResponse;
import com.odin.catalog.inventory.api.v1.dto.DatasetResponse;
import com.odin.catalog.inventory.api.v1.dto.DatasetSemanticContext;
import com.odin.catalog.inventory.api.v1.dto.DatasetSemanticTagRequest;
import com.odin.catalog.inventory.api.v1.dto.DatasetSemanticTagResponse;
import com.odin.catalog.inventory.api.v1.dto.OwnershipProposalResponse;
import com.odin.catalog.inventory.api.v1.dto.ResolveProposalRequest;
import com.odin.catalog.inventory.api.v1.dto.SemanticRecommendationResponse;
import com.odin.catalog.inventory.api.v1.dto.PageResponse;
import com.odin.catalog.inventory.api.v1.dto.ProposeTransferRequest;
import com.odin.catalog.inventory.api.v1.dto.TermsOfUseResponse;
import com.odin.catalog.inventory.application.dataset.DatasetService;
import com.odin.catalog.inventory.application.dataset.TermsOfUseService;
import com.odin.catalog.inventory.application.logical.LogicalModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Datasets", description = "DCAT Datasets — logical representations of data assets within a catalog")
@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
public class DatasetsController {

    private static final Logger log = LoggerFactory.getLogger(DatasetsController.class);

    private final DatasetService datasetService;
    private final LogicalModelService logicalModelService;
    private final TermsOfUseService termsOfUseService;

    @Operation(summary = "List datasets",
        description = "Returns a paginated list of datasets. Filter by catalog or source URI.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of datasets"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping
    public PageResponse<DatasetResponse> list(
            @Parameter(description = "Filter by parent catalog UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestParam(required = false) UUID catalogId,
            @Parameter(description = "Filter by source URI (exact match, used to find harvested datasets)",
                example = "arn:aws:glue:eu-west-1:123456789012:table/trades/positions")
            @RequestParam(required = false) String sourceUri,
            @PageableDefault(size = 20) Pageable pageable) {
        return datasetService.list(catalogId, sourceUri, pageable);
    }

    @Operation(summary = "Get dataset", description = "Returns a single dataset by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dataset found"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public DatasetResponse get(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return datasetService.get(id);
    }

    @Operation(summary = "Get semantic context",
        description = "Returns aggregated semantic types and vocabulary concept data derived from " +
            "controlled vocabulary mappings across all logical data elements of this dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Semantic context"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}/semantic-context")
    public DatasetSemanticContext getSemanticContext(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id) {
        datasetService.get(id); // validates dataset exists
        return logicalModelService.getSemanticContext(id);
    }

    @Operation(summary = "Accept a semantic tag",
        description = "Saves an accepted AI-recommended (or manually entered) semantic type tag " +
            "directly on the dataset. Tags complement vocabulary-mapping-derived semantic types.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tag saved"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/{id}/semantic-tags")
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetSemanticTagResponse acceptSemanticTag(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id,
            @Valid @RequestBody DatasetSemanticTagRequest request) {
        log.info("action=ACCEPT_SEMANTIC_TAG datasetId={}", id);
        datasetService.get(id);
        return logicalModelService.acceptSemanticTag(id, request);
    }

    @Operation(summary = "Delete a semantic tag",
        description = "Removes a previously accepted semantic tag from the dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Tag or dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/{id}/semantic-tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSemanticTag(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id,
            @Parameter(description = "Tag UUID") @PathVariable UUID tagId) {
        log.info("action=DELETE_SEMANTIC_TAG datasetId={} tagId={}", id, tagId);
        logicalModelService.deleteSemanticTag(id, tagId);
    }

    @Operation(summary = "Get AI semantic type recommendations",
        description = "Uses the AI service to analyse this dataset's metadata and current vocabulary " +
            "mappings, then recommends additional business domain types and vocabulary concepts to " +
            "improve semantic coverage.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Semantic type recommendations"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "503", description = "AI service unavailable", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/{id}/recommend-semantic-context")
    public SemanticRecommendationResponse recommendSemanticContext(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id) {
        log.info("action=RECOMMEND_SEMANTIC_CONTEXT datasetId={}", id);
        datasetService.get(id); // validates dataset exists
        var aiResult = logicalModelService.recommendSemanticContext(id);
        var types = aiResult.types().stream()
            .map(t -> new SemanticRecommendationResponse.RecommendedType(t.type(), t.rationale(), t.vocabularyHint()))
            .toList();
        return new SemanticRecommendationResponse(types, aiResult.rationale());
    }

    @Operation(summary = "Create dataset", description = "Creates a new DCAT dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Dataset created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetResponse create(@Valid @RequestBody DatasetRequest request) {
        log.info("action=CREATE_DATASET catalogId={}", request.catalogId());
        return datasetService.create(request);
    }

    @Operation(summary = "Update dataset", description = "Replaces all fields of an existing dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dataset updated"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PutMapping("/{id}")
    public DatasetResponse update(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody DatasetRequest request) {
        log.info("action=UPDATE_DATASET id={}", id);
        return datasetService.update(id, request);
    }

    @Operation(summary = "Delete dataset", description = "Soft-deletes a dataset and its distributions.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        log.info("action=DELETE_DATASET id={}", id);
        datasetService.delete(id);
    }

    // ── Ownership ─────────────────────────────────────────────────────────────

    @Operation(summary = "Assign owner",
        description = "Assigns a data owner to an unowned dataset. The dataset must have no current owner. " +
                      "Use the transfer-proposal workflow if the dataset is already owned.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Owner assigned"),
        @ApiResponse(responseCode = "409", description = "Dataset already has an owner", content = @Content),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PutMapping("/{id}/owner")
    public DatasetResponse assignOwner(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id,
            @Valid @RequestBody AssignOwnerRequest request) {
        log.info("action=ASSIGN_OWNER datasetId={} userId={}", id, request.userId());
        return datasetService.assignOwner(id, request.userId());
    }

    @Operation(summary = "Get audit history",
        description = "Returns a reverse-chronological paginated list of audit log entries for a dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit entries"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}/history")
    public PageResponse<DatasetAuditResponse> getHistory(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return datasetService.getHistory(id, pageable);
    }

    @Operation(summary = "Get logical element change history for a dataset",
        description = "Returns a reverse-chronological paginated list of audit log entries for all logical data element mutations within the dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Element audit entries"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}/element-history")
    public PageResponse<LogicalElementAuditResponse> getElementHistory(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return datasetService.getElementHistory(id, pageable);
    }

    // ── Ownership transfer proposals ──────────────────────────────────────────

    @Operation(summary = "Propose ownership transfer",
        description = "Submits a proposal to transfer dataset ownership to another user. " +
                      "The current owner must approve before the transfer takes effect.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Proposal created"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/{id}/ownership-proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnershipProposalResponse proposeTransfer(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id,
            @Valid @RequestBody ProposeTransferRequest request) {
        log.info("action=PROPOSE_TRANSFER datasetId={} proposedOwnerId={}", id, request.proposedOwnerId());
        return datasetService.proposeTransfer(id, request.proposedOwnerId());
    }

    @Operation(summary = "Approve ownership transfer",
        description = "Approves a pending transfer proposal. Only the current owner or a catalog admin may call this.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transfer approved; dataset ownerId updated"),
        @ApiResponse(responseCode = "400", description = "Proposal is not in PENDING state", content = @Content),
        @ApiResponse(responseCode = "403", description = "Caller is not the current owner", content = @Content),
        @ApiResponse(responseCode = "404", description = "Dataset or proposal not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/{id}/ownership-proposals/{proposalId}/approve")
    public DatasetResponse approveTransfer(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id,
            @Parameter(description = "Proposal UUID") @PathVariable UUID proposalId,
            @RequestBody(required = false) ResolveProposalRequest request) {
        log.info("action=APPROVE_TRANSFER datasetId={} proposalId={}", id, proposalId);
        return datasetService.approveTransfer(id, proposalId, request != null ? request.note() : null);
    }

    @Operation(summary = "Reject ownership transfer",
        description = "Rejects a pending transfer proposal. Only the current owner or a catalog admin may call this.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proposal rejected"),
        @ApiResponse(responseCode = "400", description = "Proposal is not in PENDING state", content = @Content),
        @ApiResponse(responseCode = "403", description = "Caller is not the current owner", content = @Content),
        @ApiResponse(responseCode = "404", description = "Dataset or proposal not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/{id}/ownership-proposals/{proposalId}/reject")
    public OwnershipProposalResponse rejectTransfer(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id,
            @Parameter(description = "Proposal UUID") @PathVariable UUID proposalId,
            @RequestBody(required = false) ResolveProposalRequest request) {
        log.info("action=REJECT_TRANSFER datasetId={} proposalId={}", id, proposalId);
        return datasetService.rejectTransfer(id, proposalId, request != null ? request.note() : null);
    }

    @Operation(summary = "Get pending ownership proposal",
        description = "Returns the current PENDING proposal for this dataset, if one exists.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pending proposal (or empty 204 if none)"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}/ownership-proposals/pending")
    public ResponseEntity<OwnershipProposalResponse> getPendingProposal(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id) {
        return datasetService.getPendingProposal(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "Get terms of use",
        description = "Derives an ODRL terms-of-use policy from accepted element classifications " +
            "and controlled vocabulary concept mappings. Returns an explicit stored policy if one has been set.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Terms of use policy"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}/terms-of-use")
    public TermsOfUseResponse getTermsOfUse(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id) {
        return termsOfUseService.derive(id);
    }

    @Operation(summary = "Accept terms of use",
        description = "Locks the currently derived ODRL policy as the official terms of use for this dataset " +
            "by persisting it to hasPolicy. Subsequent GET will return policySource=explicit.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Policy accepted and persisted"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/{id}/terms-of-use/accept")
    public TermsOfUseResponse acceptTermsOfUse(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id) {
        log.info("action=ACCEPT_TERMS_OF_USE datasetId={}", id);
        return termsOfUseService.accept(id);
    }

    @Operation(summary = "Reset terms of use",
        description = "Clears any explicitly stored ODRL policy, reverting to dynamic derivation from " +
            "element classifications and vocabulary concepts.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Policy reset; derived terms returned"),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/{id}/terms-of-use/policy")
    public TermsOfUseResponse resetTermsOfUse(
            @Parameter(description = "Dataset UUID") @PathVariable UUID id) {
        log.info("action=RESET_TERMS_OF_USE datasetId={}", id);
        return termsOfUseService.reset(id);
    }
}
