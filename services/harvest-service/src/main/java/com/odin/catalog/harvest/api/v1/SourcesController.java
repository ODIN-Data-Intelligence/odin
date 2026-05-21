package com.odin.catalog.harvest.api.v1;

import com.odin.catalog.harvest.application.HarvestSourceService;
import com.odin.catalog.harvest.api.v1.dto.HarvestSourceRequest;
import com.odin.catalog.harvest.api.v1.dto.HarvestSourceResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Sources", description = "Harvest source configurations — connection details for external data systems (DCAT HTTP, AWS Glue, Snowflake, Teradata)")
@RestController
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
public class SourcesController {

    private final HarvestSourceService sourceService;

    @Operation(summary = "List harvest sources",
        description = "Returns all configured harvest sources. Filter by connector type.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of harvest sources"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping
    public List<HarvestSourceResponse> list(
            @Parameter(description = "Filter by connector type",
                schema = @Schema(allowableValues = {"dcat_http", "aws_glue", "snowflake", "teradata"}))
            @RequestParam(required = false) String type) {
        return sourceService.list(type);
    }

    @Operation(summary = "Get harvest source", description = "Returns a single harvest source by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Harvest source found"),
        @ApiResponse(responseCode = "404", description = "Harvest source not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public HarvestSourceResponse get(
            @Parameter(description = "Harvest source UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return sourceService.get(id);
    }

    @Operation(summary = "Create harvest source",
        description = "Registers a new external data source for harvesting. Credentials must be stored separately via the credential API.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Harvest source created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HarvestSourceResponse create(@Valid @RequestBody HarvestSourceRequest request) {
        return sourceService.create(request);
    }

    @Operation(summary = "Update harvest source", description = "Replaces all fields of an existing harvest source.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Harvest source updated"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Harvest source not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PutMapping("/{id}")
    public HarvestSourceResponse update(
            @Parameter(description = "Harvest source UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody HarvestSourceRequest request) {
        return sourceService.update(id, request);
    }

    @Operation(summary = "Delete harvest source",
        description = "Permanently deletes a harvest source and all associated jobs. Running jobs are not affected.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Harvest source not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Harvest source UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        sourceService.delete(id);
    }

    @Operation(summary = "Test source connection",
        description = "Attempts to connect to the external data source using its stored credentials. Returns success/failure and a human-readable message.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connection test result — check 'success' field",
            content = @Content(schema = @Schema(example = "{\"success\": true, \"message\": \"Connection successful\"}"))),
        @ApiResponse(responseCode = "404", description = "Harvest source not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/{id}/test")
    public Map<String, Object> testConnection(
            @Parameter(description = "Harvest source UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        boolean ok = sourceService.testConnection(id);
        return Map.of("success", ok, "message", ok ? "Connection successful" : "Connection failed");
    }
}
