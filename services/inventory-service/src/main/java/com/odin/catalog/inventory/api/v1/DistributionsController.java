package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.api.v1.dto.ColumnElementSuggestion;
import com.odin.catalog.inventory.api.v1.dto.CsvwColumnRequest;
import com.odin.catalog.inventory.api.v1.dto.CsvwColumnResponse;
import com.odin.catalog.inventory.api.v1.dto.DistributionRequest;
import com.odin.catalog.inventory.api.v1.dto.DistributionResponse;
import com.odin.catalog.inventory.application.distribution.DistributionService;
import com.odin.catalog.inventory.application.logical.LogicalModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Distributions", description = "DCAT Distributions — physical access points and CSV-W column schemas for datasets")
@RestController
@RequiredArgsConstructor
public class DistributionsController {

    private final DistributionService distributionService;
    private final LogicalModelService logicalModelService;

    @Operation(summary = "List all distributions",
        description = "Returns all non-deleted distributions for the current tenant, paginated and sorted by creation date descending.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of distributions"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/distributions")
    public Page<DistributionResponse> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return distributionService.listAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @Operation(summary = "List distributions for a dataset",
        description = "Returns all non-deleted distributions belonging to the given dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of distributions"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/datasets/{datasetId}/distributions")
    public List<DistributionResponse> listByDataset(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId) {
        return distributionService.listByDataset(datasetId);
    }

    @Operation(summary = "Create a distribution for a dataset",
        description = "Adds a new DCAT Distribution (e.g. Parquet file, Snowflake endpoint, REST API) to a dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Distribution created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/datasets/{datasetId}/distributions")
    @ResponseStatus(HttpStatus.CREATED)
    public DistributionResponse create(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId,
            @Valid @RequestBody DistributionRequest request) {
        return distributionService.create(datasetId, request);
    }

    @Operation(summary = "Get distribution", description = "Returns a single distribution by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Distribution found"),
        @ApiResponse(responseCode = "404", description = "Distribution not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/distributions/{id}")
    public DistributionResponse get(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return distributionService.get(id);
    }

    @Operation(summary = "Update a distribution",
        description = "Replaces all fields of an existing distribution.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Distribution updated"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Distribution not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PutMapping("/api/v1/distributions/{id}")
    public DistributionResponse update(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody DistributionRequest request) {
        return distributionService.update(id, request);
    }

    @Operation(summary = "Delete distribution", description = "Soft-deletes a distribution.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Distribution not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/api/v1/distributions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        distributionService.delete(id);
    }

    @Operation(summary = "Get physical schema for a dataset",
        description = "Returns the CSV-W column schema harvested for this dataset. Columns are ordered by their ordinal position.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of physical columns"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/datasets/{datasetId}/physical-schema")
    public List<CsvwColumnResponse> getPhysicalSchema(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId) {
        return distributionService.getPhysicalSchema(datasetId);
    }

    @Operation(summary = "Set physical schema for a dataset",
        description = "Replaces the full CSV-W column schema for a dataset. All existing columns are deleted and replaced. Used by the harvest pipeline.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Schema replaced"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/datasets/{datasetId}/physical-schema")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CsvwColumnResponse> setPhysicalSchema(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId,
            @Valid @RequestBody List<@Valid CsvwColumnRequest> columns) {
        return distributionService.setPhysicalSchema(datasetId, columns);
    }

    @Operation(summary = "Get physical schema for a distribution",
        description = "Returns the CSV-W column schema for a specific distribution.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of physical columns"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/distributions/{distributionId}/physical-schema")
    public List<CsvwColumnResponse> getDistributionPhysicalSchema(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID distributionId) {
        return distributionService.getPhysicalSchema(distributionId);
    }

    @Operation(summary = "Set physical schema for a distribution",
        description = "Replaces the full CSV-W column schema for a specific distribution.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Schema replaced"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/distributions/{distributionId}/physical-schema")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CsvwColumnResponse> setDistributionPhysicalSchema(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID distributionId,
            @Valid @RequestBody List<@Valid CsvwColumnRequest> columns) {
        return distributionService.setPhysicalSchema(distributionId, columns);
    }

    @Operation(summary = "Suggest logical element bindings for a distribution",
        description = "Returns confidence-scored suggestions for binding physical columns to logical data elements in the given model. "
            + "Matching is done by name similarity.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of column-to-element suggestions"),
        @ApiResponse(responseCode = "404", description = "Distribution or model not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/distributions/{distributionId}/suggest-element-mappings")
    public List<ColumnElementSuggestion> suggestElementMappings(
            @Parameter(description = "Distribution UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID distributionId,
            @Parameter(description = "Logical model UUID to match against", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestParam UUID modelId) {
        return logicalModelService.suggestElementMappings(distributionId, modelId);
    }
}
