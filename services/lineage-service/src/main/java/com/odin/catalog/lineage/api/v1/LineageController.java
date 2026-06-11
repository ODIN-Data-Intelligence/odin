package com.odin.catalog.lineage.api.v1;

import com.odin.catalog.lineage.api.v1.dto.CatalogLinkRequest;
import com.odin.catalog.lineage.api.v1.dto.LineageGraphResponse;
import com.odin.catalog.lineage.api.v1.dto.LineageIdentityResponse;
import com.odin.catalog.lineage.api.v1.dto.LineageLookupResponse;
import com.odin.catalog.lineage.application.LineageGraphService;
import com.odin.catalog.lineage.ingestion.OpenLineageHandler;
import com.odin.catalog.lineage.infrastructure.jpa.repository.LineageDatasetRepository;
import com.odin.catalog.shared.models.openlineage.RunEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Lineage", description = "OpenLineage event ingestion and graph traversal — upstream, downstream, impact analysis, and catalog linking")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LineageController {

    private static final Logger log = LoggerFactory.getLogger(LineageController.class);

    private final OpenLineageHandler openLineageHandler;
    private final LineageGraphService lineageGraphService;
    private final LineageDatasetRepository lineageDatasetRepository;

    @Operation(summary = "Ingest an OpenLineage run event",
        description = "Accepts an OpenLineage 1.x RunEvent and persists the job, run, and dataset nodes to the Apache AGE graph. "
            + "Compatible with Marquez, dbt, Spark, and any OpenLineage 1.x producer. "
            + "On COMPLETE events, DERIVED_FROM edges are created between all input and output datasets.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Run event ingested successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid OpenLineage event structure", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/lineage")
    @ResponseStatus(HttpStatus.CREATED)
    public void ingestRunEvent(@RequestBody RunEvent event) {
        log.info("action=INGEST_RUN_EVENT eventType={}", event.eventType());
        openLineageHandler.handle(event);
    }

    @Operation(summary = "Resolve namespace and name to a lineage dataset UUID",
        description = "Looks up a lineage dataset by OpenLineage namespace and name, returning its lineage service UUID. "
            + "Used by the lineage explorer to resolve user-entered namespace/name to an ID before querying the graph.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lineage dataset UUID resolved"),
        @ApiResponse(responseCode = "404", description = "No lineage dataset found for this namespace and name", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/datasets/lookup")
    public ResponseEntity<LineageLookupResponse> lookupDataset(
            @Parameter(description = "OpenLineage namespace", example = "snowflake://trading_dw")
            @RequestParam String namespace,
            @Parameter(description = "OpenLineage dataset name", example = "PUBLIC.TRADE_POSITIONS")
            @RequestParam String name) {
        return lineageDatasetRepository.findByNamespaceAndName(namespace, name)
            .map(ds -> ResponseEntity.ok(new LineageLookupResponse(ds.getId())))
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get lineage graph for a dataset",
        description = "Traverses the Apache AGE graph and returns nodes and edges reachable from the given dataset "
            + "in the requested direction. Uses Cypher multi-hop queries (DERIVED_FROM edges) up to the specified depth. "
            + "Each node includes its lineage UUID and optionally its catalog resource UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lineage graph nodes and edges"),
        @ApiResponse(responseCode = "404", description = "Lineage dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/datasets/{id}/lineage")
    public ResponseEntity<LineageGraphResponse> getLineage(
            @Parameter(description = "Lineage service dataset UUID")
            @PathVariable UUID id,
            @Parameter(description = "Traversal direction from the root dataset",
                schema = @Schema(allowableValues = {"upstream", "downstream"}))
            @RequestParam(defaultValue = "upstream") String direction,
            @Parameter(description = "Maximum number of hops to traverse (1–10)", example = "5")
            @RequestParam(defaultValue = "5") int depth) {
        return lineageGraphService.buildGraph(id, direction, depth)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get downstream impact analysis for a dataset",
        description = "Returns all datasets downstream of the given dataset — i.e. datasets that would be affected if this dataset changes. "
            + "Equivalent to a downstream lineage traversal.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Downstream impact graph"),
        @ApiResponse(responseCode = "404", description = "Lineage dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/datasets/{id}/impact")
    public ResponseEntity<LineageGraphResponse> getImpact(
            @Parameter(description = "Lineage service dataset UUID")
            @PathVariable UUID id,
            @Parameter(description = "Maximum number of downstream hops to traverse", example = "10")
            @RequestParam(defaultValue = "10") int depth) {
        return lineageGraphService.buildImpact(id, depth)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Resolve lineage identity for a catalog dataset",
        description = "Looks up the lineage UUID, namespace, and name for a given ODIN catalog dataset UUID. "
            + "Used by the consumer UI to fetch lineage for a dataset shown in the drawer.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lineage identity resolved"),
        @ApiResponse(responseCode = "404", description = "No lineage dataset linked to this catalog ID", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/catalog-datasets/{catalogId}/lineage-identity")
    public ResponseEntity<LineageIdentityResponse> getLineageIdentity(
            @Parameter(description = "ODIN catalog dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID catalogId) {
        return lineageDatasetRepository.findByCatalogResourceId(catalogId)
            .map(ds -> ResponseEntity.ok(
                new LineageIdentityResponse(ds.getId(), ds.getNamespace(), ds.getName())))
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Link a catalog dataset to a lineage dataset",
        description = "Associates an ODIN catalog dataset UUID with an existing OpenLineage dataset (namespace + name). "
            + "This link enables the consumer UI to show lineage data for catalog datasets.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Link saved"),
        @ApiResponse(responseCode = "404", description = "Lineage dataset not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PutMapping("/datasets/{namespace}/{name}/catalog-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkCatalogResource(
            @Parameter(description = "OpenLineage namespace", example = "snowflake://trading_dw")
            @PathVariable String namespace,
            @Parameter(description = "OpenLineage dataset name", example = "PUBLIC.TRADE_POSITIONS")
            @PathVariable String name,
            @Valid @RequestBody CatalogLinkRequest request) {
        lineageGraphService.linkCatalogResource(namespace, name, request.catalogResourceId());
    }
}
