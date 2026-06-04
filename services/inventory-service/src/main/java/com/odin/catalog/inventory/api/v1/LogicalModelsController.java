package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.api.v1.dto.*;
import com.odin.catalog.inventory.application.logical.BulkRecommendationJobRegistry;
import com.odin.catalog.inventory.application.logical.LogicalModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Tag(name = "Logical Models", description = "Logical data models — semantic business view of a dataset's structure")
@RestController
@RequiredArgsConstructor
public class LogicalModelsController {

    private final LogicalModelService logicalModelService;
    private final BulkRecommendationJobRegistry jobRegistry;

    // ── Logical Models ────────────────────────────────────────────────────────

    @Operation(summary = "List logical models for a dataset",
        description = "Returns all logical model versions for the given dataset, ordered by creation date. "
            + "A dataset can have multiple model versions (draft, published, deprecated).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of logical models"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/datasets/{datasetId}/logical-models")
    public List<LogicalModelResponse> listForDataset(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId) {
        return logicalModelService.listForDataset(datasetId);
    }

    @Operation(summary = "Create a logical model for a dataset",
        description = "Creates a new logical model (initially in 'draft' status) for the given dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Logical model created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/datasets/{datasetId}/logical-models")
    @ResponseStatus(HttpStatus.CREATED)
    public LogicalModelResponse create(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId,
            @Valid @RequestBody LogicalModelRequest request) {
        return logicalModelService.create(datasetId, request);
    }

    @Operation(summary = "Get logical model", description = "Returns a single logical model with its elements.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logical model found"),
        @ApiResponse(responseCode = "404", description = "Logical model not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/logical-models/{id}")
    public LogicalModelResponse get(
            @Parameter(description = "Logical model UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return logicalModelService.get(id);
    }

    @Operation(summary = "Update logical model status",
        description = "Transitions the logical model to a new status. Published models are immutable — create a new version to make changes.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated"),
        @ApiResponse(responseCode = "400", description = "Invalid status", content = @Content),
        @ApiResponse(responseCode = "404", description = "Logical model not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "New model status",
        content = @Content(schema = @Schema(example = "{\"status\": \"published\"}")))
    @PatchMapping("/api/v1/logical-models/{id}/status")
    public LogicalModelResponse updateStatus(
            @Parameter(description = "Logical model UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return logicalModelService.updateStatus(id, body.get("status"));
    }

    @Operation(summary = "Delete logical model", description = "Permanently deletes a logical model and all its elements.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Logical model not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/api/v1/logical-models/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Logical model UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        logicalModelService.delete(id);
    }

    // ── Logical Data Elements ─────────────────────────────────────────────────

    @Operation(summary = "List elements in a logical model",
        description = "Returns all logical data elements belonging to the model, ordered by ordinal.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of logical data elements"),
        @ApiResponse(responseCode = "404", description = "Logical model not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/logical-models/{modelId}/elements")
    public List<LogicalDataElementResponse> listElements(
            @Parameter(description = "Logical model UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID modelId) {
        return logicalModelService.listElements(modelId);
    }

    @Operation(summary = "Add an element to a logical model",
        description = "Creates a new logical data element within the model.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Element created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Logical model not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-models/{modelId}/elements")
    @ResponseStatus(HttpStatus.CREATED)
    public LogicalDataElementResponse addElement(
            @Parameter(description = "Logical model UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID modelId,
            @Valid @RequestBody LogicalDataElementRequest request) {
        return logicalModelService.addElement(modelId, request);
    }

    @Operation(summary = "Update a logical data element", description = "Replaces all fields of an existing logical data element.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Element updated"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PutMapping("/api/v1/logical-data-elements/{elementId}")
    public LogicalDataElementResponse updateElement(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId,
            @Valid @RequestBody LogicalDataElementRequest request) {
        return logicalModelService.updateElement(elementId, request);
    }

    @Operation(summary = "Bind element to a physical column",
        description = "Links a logical data element to a harvested physical column (csvw_columns row). "
            + "This binding enables the consumer UI to show the physical column name and datatype alongside the business concept.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Element bound to physical column"),
        @ApiResponse(responseCode = "404", description = "Element or column not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Physical column to bind",
        content = @Content(schema = @Schema(example = "{\"physicalColumnId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}")))
    @PostMapping("/api/v1/logical-data-elements/{elementId}/bind")
    public LogicalDataElementResponse bindPhysicalColumn(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId,
            @RequestBody Map<String, UUID> body) {
        return logicalModelService.bindPhysicalColumn(elementId, body.get("physicalColumnId"));
    }

    @Operation(summary = "Unbind physical column from element",
        description = "Removes the physical column binding from a logical data element.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Binding removed"),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/api/v1/logical-data-elements/{elementId}/bind")
    public LogicalDataElementResponse unbindPhysicalColumn(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId) {
        return logicalModelService.unbindPhysicalColumn(elementId);
    }

    @Operation(summary = "Delete a logical data element", description = "Permanently deletes a logical data element and its vocabulary mappings.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/api/v1/logical-data-elements/{elementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteElement(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId) {
        logicalModelService.deleteElement(elementId);
    }

    // ── Vocabulary Mappings ───────────────────────────────────────────────────

    @Operation(summary = "List vocabulary mappings for an element",
        description = "Returns all SKOS vocabulary mappings attached to this logical data element.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of vocabulary mappings"),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/logical-data-elements/{elementId}/vocab-mappings")
    public List<VocabMappingResponse> listMappings(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId) {
        return logicalModelService.listVocabMappings(elementId);
    }

    @Operation(summary = "Add a vocabulary mapping to an element",
        description = "Attaches a SKOS mapping (e.g. exactMatch to a FIBO concept IRI) to a logical data element.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Vocabulary mapping created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-data-elements/{elementId}/vocab-mappings")
    @ResponseStatus(HttpStatus.CREATED)
    public VocabMappingResponse addMapping(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId,
            @Valid @RequestBody VocabMappingRequest request) {
        return logicalModelService.addVocabMapping(elementId, request);
    }

    @Operation(summary = "Delete a vocabulary mapping", description = "Permanently removes a vocabulary mapping.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Mapping not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/api/v1/vocab-mappings/{mappingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMapping(
            @Parameter(description = "Vocabulary mapping UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID mappingId) {
        logicalModelService.deleteVocabMapping(mappingId);
    }

    // ── Classification ────────────────────────────────────────────────────────

    @Operation(summary = "Request AI classification recommendation for an element",
        description = "Calls the AI service to infer a classification level from the element's name, type, and vocabulary mappings. "
            + "The result is stored as a pending recommendation; the data owner must Accept or Reject it.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recommendation stored"),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-data-elements/{elementId}/recommend-classification")
    public LogicalDataElementResponse recommendClassification(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId) {
        return logicalModelService.recommendClassification(elementId);
    }

    @Operation(summary = "Accept the pending AI classification recommendation",
        description = "Copies the recommended classification to the accepted classification and clears the recommendation.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Classification accepted"),
        @ApiResponse(responseCode = "404", description = "Element not found or no pending recommendation", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-data-elements/{elementId}/accept-classification")
    public LogicalDataElementResponse acceptClassification(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId) {
        return logicalModelService.acceptClassification(elementId);
    }

    @Operation(summary = "Reject the pending AI classification recommendation",
        description = "Clears the pending recommendation without changing the accepted classification.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recommendation rejected"),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-data-elements/{elementId}/reject-classification")
    public LogicalDataElementResponse rejectClassification(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId) {
        return logicalModelService.rejectClassification(elementId);
    }

    @Operation(summary = "Request AI description recommendation",
        description = "Uses the AI service to generate a description for this element based on its vocabulary concept mappings. "
            + "The recommended description is stored as a pending suggestion and must be accepted or rejected.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recommendation generated"),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "503", description = "AI service unavailable", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-data-elements/{elementId}/recommend-description")
    public LogicalDataElementResponse recommendDescription(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId) {
        return logicalModelService.recommendDescription(elementId);
    }

    @Operation(summary = "Accept the pending AI description recommendation",
        description = "Copies the recommended description to the element's description and clears the recommendation.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Description accepted"),
        @ApiResponse(responseCode = "404", description = "Element not found or no pending recommendation", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-data-elements/{elementId}/accept-description")
    public LogicalDataElementResponse acceptDescription(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId) {
        return logicalModelService.acceptDescription(elementId);
    }

    @Operation(summary = "Reject the pending AI description recommendation",
        description = "Clears the pending description recommendation without changing the current description.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recommendation rejected"),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-data-elements/{elementId}/reject-description")
    public LogicalDataElementResponse rejectDescription(
            @Parameter(description = "Logical data element UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID elementId) {
        return logicalModelService.rejectDescription(elementId);
    }

    @Operation(summary = "Batch-recommend classifications for all elements in a model",
        description = "Enqueues an AI classification job for every element in the model. "
            + "Returns 202 Accepted with a jobId — poll GET .../jobs/{jobId} for status.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Job accepted"),
        @ApiResponse(responseCode = "404", description = "Logical model not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-models/{modelId}/recommend-classifications")
    public ResponseEntity<BulkRecommendationJobResponse> recommendModelClassifications(
            @Parameter(description = "Logical model UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID modelId) {
        UUID jobId = jobRegistry.register(modelId);
        logicalModelService.recommendModelClassifications(modelId, jobId);
        return ResponseEntity.accepted().body(toJobResponse(jobRegistry.get(jobId).orElseThrow()));
    }

    @Operation(summary = "Get bulk recommendation job status",
        description = "Returns the current status of a batch classification job. "
            + "Poll until status is COMPLETED or FAILED, then fetch elements for results.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job status"),
        @ApiResponse(responseCode = "404", description = "Job not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/logical-models/recommend-classifications/jobs/{jobId}")
    public BulkRecommendationJobResponse getRecommendationJob(
            @Parameter(description = "Job UUID returned by the POST endpoint")
            @PathVariable UUID jobId) {
        return jobRegistry.get(jobId)
            .map(this::toJobResponse)
            .orElseThrow(() -> new NoSuchElementException("Recommendation job not found: " + jobId));
    }

    @Operation(summary = "Batch-recommend descriptions for all elements in a model",
        description = "Enqueues an AI description job for every element in the model. "
            + "Returns 202 Accepted with a jobId — poll GET .../jobs/{jobId} for status.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Job accepted"),
        @ApiResponse(responseCode = "404", description = "Logical model not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-models/{modelId}/recommend-descriptions")
    public ResponseEntity<BulkRecommendationJobResponse> recommendModelDescriptions(
            @Parameter(description = "Logical model UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID modelId) {
        UUID jobId = jobRegistry.register(modelId);
        logicalModelService.recommendModelDescriptions(modelId, jobId);
        return ResponseEntity.accepted().body(toJobResponse(jobRegistry.get(jobId).orElseThrow()));
    }

    @Operation(summary = "Get bulk description recommendation job status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job status"),
        @ApiResponse(responseCode = "404", description = "Job not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/logical-models/recommend-descriptions/jobs/{jobId}")
    public BulkRecommendationJobResponse getDescriptionRecommendationJob(
            @Parameter(description = "Job UUID returned by the POST endpoint")
            @PathVariable UUID jobId) {
        return jobRegistry.get(jobId)
            .map(this::toJobResponse)
            .orElseThrow(() -> new NoSuchElementException("Description recommendation job not found: " + jobId));
    }

    @Operation(summary = "Request AI vocabulary concept recommendations for an element",
        description = "Calls the AI service to suggest relevant SKOS concept mappings based on the element's name, "
            + "type, and existing mappings. Results are stored as pending recommendations for owner review.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recommendations generated"),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-data-elements/{elementId}/recommend-vocab-concepts")
    public LogicalDataElementResponse recommendVocabConcepts(
            @Parameter(description = "Logical data element UUID") @PathVariable UUID elementId) {
        return logicalModelService.recommendVocabConcepts(elementId);
    }

    @Operation(summary = "Accept pending vocabulary concept recommendations",
        description = "Creates VocabMappingEntity rows for each recommended concept and clears the recommendation.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recommendations accepted"),
        @ApiResponse(responseCode = "404", description = "Element not found or no pending recommendation", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-data-elements/{elementId}/accept-vocab-concepts")
    public LogicalDataElementResponse acceptVocabConcepts(
            @Parameter(description = "Logical data element UUID") @PathVariable UUID elementId) {
        return logicalModelService.acceptVocabConcepts(elementId);
    }

    @Operation(summary = "Reject pending vocabulary concept recommendations",
        description = "Clears the pending vocabulary recommendations without creating any mappings.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recommendations rejected"),
        @ApiResponse(responseCode = "404", description = "Element not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-data-elements/{elementId}/reject-vocab-concepts")
    public LogicalDataElementResponse rejectVocabConcepts(
            @Parameter(description = "Logical data element UUID") @PathVariable UUID elementId) {
        return logicalModelService.rejectVocabConcepts(elementId);
    }

    @Operation(summary = "Batch-recommend vocabulary concepts for all elements in a model",
        description = "Asynchronously generates vocabulary concept recommendations for every element in the model. "
            + "Returns a job ID that can be polled for status.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Job accepted"),
        @ApiResponse(responseCode = "404", description = "Model not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/logical-models/{modelId}/recommend-vocab-concepts")
    public ResponseEntity<BulkRecommendationJobResponse> recommendModelVocabConcepts(
            @Parameter(description = "Logical model UUID") @PathVariable UUID modelId) {
        UUID jobId = jobRegistry.register(modelId);
        logicalModelService.recommendModelVocabConcepts(modelId, jobId);
        return ResponseEntity.accepted().body(toJobResponse(jobRegistry.get(jobId).orElseThrow()));
    }

    @Operation(summary = "Get bulk vocabulary concept recommendation job status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job status"),
        @ApiResponse(responseCode = "404", description = "Job not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/logical-models/recommend-vocab-concepts/jobs/{jobId}")
    public BulkRecommendationJobResponse getVocabRecommendationJob(
            @Parameter(description = "Job UUID returned by the POST endpoint") @PathVariable UUID jobId) {
        return jobRegistry.get(jobId)
            .map(this::toJobResponse)
            .orElseThrow(() -> new NoSuchElementException("Vocabulary recommendation job not found: " + jobId));
    }

    private BulkRecommendationJobResponse toJobResponse(BulkRecommendationJobRegistry.Job job) {
        return new BulkRecommendationJobResponse(
            job.jobId().toString(), job.modelId().toString(), job.status().name(),
            job.createdAt(), job.completedAt(), job.error()
        );
    }
}
