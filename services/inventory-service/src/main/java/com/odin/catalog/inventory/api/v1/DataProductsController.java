package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.api.v1.dto.DataProductRequest;
import com.odin.catalog.inventory.api.v1.dto.DataProductResponse;
import com.odin.catalog.inventory.api.v1.dto.DatasetResponse;
import com.odin.catalog.inventory.api.v1.dto.PageResponse;
import com.odin.catalog.inventory.application.dataproduct.DataProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Data Products", description = "DPROD data products — business-level data ownership, lifecycle management, and dataset linking")
@RestController
@RequestMapping("/api/v1/data-products")
@RequiredArgsConstructor
public class DataProductsController {

    private final DataProductService dataProductService;

    @Operation(summary = "List data products",
        description = "Returns a paginated list of data products. Optionally filter by domain or lifecycle status.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of data products"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions", content = @Content)
    })
    @GetMapping
    public PageResponse<DataProductResponse> list(
            @Parameter(description = "Filter by domain UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestParam(required = false) UUID domainId,
            @Parameter(description = "Filter by lifecycle status",
                schema = @Schema(allowableValues = {"Ideation", "Design", "Build", "Deploy", "Consume"}))
            @RequestParam(required = false) String lifecycleStatus,
            @PageableDefault(size = 20) Pageable pageable) {
        return dataProductService.list(domainId, lifecycleStatus, pageable);
    }

    @Operation(summary = "Get data product", description = "Returns a single data product by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data product found"),
        @ApiResponse(responseCode = "404", description = "Data product not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public DataProductResponse get(
            @Parameter(description = "Data product UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return dataProductService.get(id);
    }

    @Operation(summary = "Create data product", description = "Creates a new DPROD data product.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Data product created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataProductResponse create(@Valid @RequestBody DataProductRequest request) {
        return dataProductService.create(request);
    }

    @Operation(summary = "Update data product", description = "Replaces all fields of an existing data product.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data product updated"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Data product not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PutMapping("/{id}")
    public DataProductResponse update(
            @Parameter(description = "Data product UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody DataProductRequest request) {
        return dataProductService.update(id, request);
    }

    @Operation(summary = "Transition lifecycle status",
        description = "Moves the data product to the specified lifecycle status. "
            + "Valid values: Ideation, Design, Build, Deploy, Consume.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lifecycle status updated"),
        @ApiResponse(responseCode = "400", description = "Invalid status value", content = @Content),
        @ApiResponse(responseCode = "404", description = "Data product not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "New lifecycle status",
        content = @Content(schema = @Schema(example = "{\"status\": \"Deploy\"}")))
    @PatchMapping("/{id}/lifecycle")
    public DataProductResponse transitionLifecycle(
            @Parameter(description = "Data product UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return dataProductService.transitionLifecycle(id, body.get("status"));
    }

    @Operation(summary = "Delete data product", description = "Soft-deletes a data product.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Data product not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Data product UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        dataProductService.delete(id);
    }

    @Operation(summary = "List linked datasets",
        description = "Returns all datasets associated with this data product.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of linked datasets"),
        @ApiResponse(responseCode = "404", description = "Data product not found", content = @Content)
    })
    @GetMapping("/{id}/datasets")
    public List<DatasetResponse> getLinkedDatasets(
            @Parameter(description = "Data product UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return dataProductService.getLinkedDatasets(id);
    }

    @Operation(summary = "Link a dataset to this data product",
        description = "Associates a dataset with this data product.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Dataset linked successfully"),
        @ApiResponse(responseCode = "404", description = "Data product not found", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Dataset to link",
        content = @Content(schema = @Schema(example = "{\"datasetId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}")))
    @PostMapping("/{id}/datasets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkDataset(
            @Parameter(description = "Data product UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        dataProductService.linkDataset(id, UUID.fromString(body.get("datasetId")));
    }

    @Operation(summary = "Unlink a dataset from this data product",
        description = "Removes the association between a dataset and this data product.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Dataset unlinked successfully"),
        @ApiResponse(responseCode = "404", description = "Data product or dataset not found", content = @Content)
    })
    @DeleteMapping("/{id}/datasets/{datasetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkDataset(
            @Parameter(description = "Data product UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Parameter(description = "Dataset UUID to unlink", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID datasetId) {
        dataProductService.unlinkDataset(id, datasetId);
    }
}
