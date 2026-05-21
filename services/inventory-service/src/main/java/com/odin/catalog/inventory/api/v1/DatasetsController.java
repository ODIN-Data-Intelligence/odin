package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.api.v1.dto.DatasetRequest;
import com.odin.catalog.inventory.api.v1.dto.DatasetResponse;
import com.odin.catalog.inventory.api.v1.dto.PageResponse;
import com.odin.catalog.inventory.application.dataset.DatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    private final DatasetService datasetService;

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

    @Operation(summary = "Create dataset", description = "Creates a new DCAT dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Dataset created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetResponse create(@Valid @RequestBody DatasetRequest request) {
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
        datasetService.delete(id);
    }
}
