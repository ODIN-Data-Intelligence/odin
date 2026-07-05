package com.odin.catalog.harvest.api.v1;

import com.odin.catalog.harvest.api.v1.dto.HarvestJobRequest;
import com.odin.catalog.harvest.api.v1.dto.HarvestJobResponse;
import com.odin.catalog.harvest.api.v1.dto.HarvestRunResponse;
import com.odin.catalog.harvest.application.HarvestJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Jobs", description = "Harvest jobs — scheduled or manual execution plans linked to a source. Trigger a run to start harvesting immediately.")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobsController {

    private final HarvestJobService jobService;

    @Operation(summary = "List harvest jobs",
        description = "Returns all harvest jobs. Optionally filter by source UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of harvest jobs"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping
    public List<HarvestJobResponse> list(
            @Parameter(description = "Filter by harvest source UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestParam(required = false) UUID sourceId) {
        return jobService.list(sourceId);
    }

    @Operation(summary = "Get harvest job", description = "Returns a single harvest job by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Harvest job found"),
        @ApiResponse(responseCode = "404", description = "Harvest job not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public HarvestJobResponse get(
            @Parameter(description = "Harvest job UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return jobService.get(id);
    }

    @Operation(summary = "Create harvest job",
        description = "Creates a new harvest job for the given source. If a scheduleCron is provided, the job will run automatically on that schedule via Quartz.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Harvest job created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HarvestJobResponse create(@Valid @RequestBody HarvestJobRequest request) {
        return jobService.create(request);
    }

    @Operation(summary = "Update harvest job",
        description = "Replaces configuration fields of an existing harvest job. Does not affect in-progress runs.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Harvest job updated"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Harvest job not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PutMapping("/{id}")
    public HarvestJobResponse update(
            @Parameter(description = "Harvest job UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody HarvestJobRequest request) {
        return jobService.update(id, request);
    }

    @Operation(summary = "Delete harvest job",
        description = "Permanently deletes a harvest job. In-progress runs are not affected.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Harvest job not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Harvest job UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        jobService.delete(id);
    }

    @Operation(summary = "Trigger a harvest job run",
        description = "Immediately enqueues a run for the given job, regardless of schedule. "
            + "Returns the created run record with status 'pending'. The run is processed asynchronously by Spring Batch.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Run accepted — processing asynchronously"),
        @ApiResponse(responseCode = "404", description = "Harvest job not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/{id}/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public HarvestRunResponse trigger(
            @Parameter(description = "Harvest job UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return jobService.trigger(id);
    }
}
