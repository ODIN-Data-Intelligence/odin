package com.odin.catalog.harvest.api.v1;

import com.odin.catalog.harvest.api.v1.dto.HarvestJobRequest;
import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestJobEntity;
import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestRunEntity;
import com.odin.catalog.harvest.infrastructure.jpa.repository.HarvestJobRepository;
import com.odin.catalog.harvest.infrastructure.jpa.repository.HarvestRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Tag(name = "Jobs", description = "Harvest jobs — scheduled or manual execution plans linked to a source. Trigger a run to start harvesting immediately.")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobsController {

    private final HarvestJobRepository jobRepository;
    private final HarvestRunRepository runRepository;

    @Operation(summary = "List harvest jobs",
        description = "Returns all harvest jobs. Optionally filter by source UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of harvest jobs"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping
    public List<HarvestJobEntity> list(
            @Parameter(description = "Filter by harvest source UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestParam(required = false) UUID sourceId) {
        if (sourceId != null) return jobRepository.findBySourceId(sourceId);
        return jobRepository.findAll();
    }

    @Operation(summary = "Get harvest job", description = "Returns a single harvest job by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Harvest job found"),
        @ApiResponse(responseCode = "404", description = "Harvest job not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public HarvestJobEntity get(
            @Parameter(description = "Harvest job UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return jobRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
    public HarvestJobEntity create(@RequestBody HarvestJobRequest request) {
        HarvestJobEntity job = new HarvestJobEntity();
        job.setSourceId(request.sourceId());
        job.setName(request.name());
        job.setScheduleCron(request.scheduleCron());
        job.setFullRefresh(request.fullRefresh());
        job.setEnabled(request.enabled());
        return jobRepository.save(job);
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
    public HarvestRunEntity trigger(
            @Parameter(description = "Harvest job UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        HarvestJobEntity job = jobRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        HarvestRunEntity run = new HarvestRunEntity();
        run.setJobId(id);
        run.setSourceId(job.getSourceId());
        run.setStatus("pending");
        run.setTriggeredBy("api");
        run.setFullRefresh(job.isFullRefresh());
        return runRepository.save(run);
    }
}
