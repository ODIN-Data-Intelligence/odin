package com.odin.catalog.lineage.api.v1;

import com.odin.catalog.lineage.api.v1.dto.LineageGraphResponse;
import com.odin.catalog.lineage.ingestion.OpenLineageHandler;
import com.odin.catalog.lineage.infrastructure.age.AgeGraphRepository;
import com.odin.catalog.lineage.infrastructure.jpa.repository.LineageDatasetRepository;
import com.odin.catalog.shared.models.openlineage.RunEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Lineage", description = "OpenLineage event ingestion and graph traversal — upstream, downstream, impact analysis, and catalog linking")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LineageController {

    private final OpenLineageHandler openLineageHandler;
    private final AgeGraphRepository ageGraph;
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
        openLineageHandler.handle(event);
    }

    @Operation(summary = "Get lineage graph for a dataset",
        description = "Traverses the Apache AGE graph and returns nodes and edges reachable from the given dataset "
            + "in the requested direction. Uses Cypher multi-hop queries (DERIVED_FROM edges) up to the specified depth.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lineage graph nodes and edges"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/datasets/{namespace}/{name}/lineage")
    public LineageGraphResponse getLineage(
            @Parameter(description = "OpenLineage namespace of the dataset", example = "snowflake://trading_dw")
            @PathVariable String namespace,
            @Parameter(description = "OpenLineage name of the dataset", example = "PUBLIC.TRADE_POSITIONS")
            @PathVariable String name,
            @Parameter(description = "Traversal direction from the root dataset",
                schema = @Schema(allowableValues = {"upstream", "downstream"}))
            @RequestParam(defaultValue = "upstream") String direction,
            @Parameter(description = "Maximum number of hops to traverse (1–10)", example = "5")
            @RequestParam(defaultValue = "5") int depth) {

        List<Map<String, Object>> nodes = "upstream".equalsIgnoreCase(direction)
            ? ageGraph.getUpstreamLineage(namespace, name, depth)
            : ageGraph.getDownstreamLineage(namespace, name, depth);

        // Prepend root node at depth 0 — the Cypher traversal starts at depth 1
        nodes.add(0, Map.of("namespace", namespace, "name", name, "depth", 0L));

        List<Map<String, Object>> edges = buildEdgesForSubgraph(namespace, name, nodes);

        return new LineageGraphResponse(namespace, name, direction, depth, nodes, edges);
    }

    @Operation(summary = "Get downstream impact analysis for a dataset",
        description = "Returns all datasets downstream of the given dataset — i.e. datasets that would be affected if this dataset changes. "
            + "Equivalent to a downstream lineage traversal.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Downstream impact graph"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/datasets/{namespace}/{name}/impact")
    public LineageGraphResponse getImpact(
            @Parameter(description = "OpenLineage namespace of the dataset", example = "snowflake://trading_dw")
            @PathVariable String namespace,
            @Parameter(description = "OpenLineage name of the dataset", example = "PUBLIC.TRADE_POSITIONS")
            @PathVariable String name,
            @Parameter(description = "Maximum number of downstream hops to traverse", example = "10")
            @RequestParam(defaultValue = "10") int depth) {
        List<Map<String, Object>> nodes = ageGraph.getDownstreamLineage(namespace, name, depth);
        nodes.add(0, Map.of("namespace", namespace, "name", name, "depth", 0L));
        List<Map<String, Object>> edges = buildEdgesForSubgraph(namespace, name, nodes);
        return new LineageGraphResponse(namespace, name, "downstream", depth, nodes, edges);
    }

    @Operation(summary = "Resolve lineage identity for a catalog dataset",
        description = "Looks up the OpenLineage namespace and name for a given ODIN catalog dataset UUID. "
            + "Used by the consumer UI to fetch lineage for a dataset shown in the drawer.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lineage namespace and name resolved",
            content = @Content(schema = @Schema(example = "{\"namespace\": \"snowflake://trading_dw\", \"name\": \"PUBLIC.TRADE_POSITIONS\"}"))),
        @ApiResponse(responseCode = "404", description = "No lineage dataset linked to this catalog ID", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/catalog-datasets/{catalogId}/lineage-identity")
    public ResponseEntity<Map<String, String>> getLineageIdentity(
            @Parameter(description = "ODIN catalog dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID catalogId) {
        return lineageDatasetRepository.findByCatalogResourceId(catalogId)
            .map(ds -> ResponseEntity.ok(Map.of("namespace", ds.getNamespace(), "name", ds.getName())))
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Resolve catalog dataset ID for a lineage dataset",
        description = "Returns the ODIN catalog dataset UUID linked to an OpenLineage namespace+name pair. "
            + "Used by lineage graph views to navigate to the catalog page on node double-click.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Catalog ID resolved",
            content = @Content(schema = @Schema(example = "{\"catalogId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"))),
        @ApiResponse(responseCode = "404", description = "No catalog link found for this lineage dataset", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/datasets/{namespace}/{name}/catalog-link")
    public ResponseEntity<Map<String, String>> getCatalogLink(
            @Parameter(description = "OpenLineage namespace", example = "snowflake://trading_dw")
            @PathVariable String namespace,
            @Parameter(description = "OpenLineage dataset name", example = "PUBLIC.TRADE_POSITIONS")
            @PathVariable String name) {
        return lineageDatasetRepository.findByNamespaceAndName(namespace, name)
            .filter(ds -> ds.getCatalogResourceId() != null)
            .map(ds -> ResponseEntity.ok(Map.of("catalogId", ds.getCatalogResourceId().toString())))
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
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Catalog resource UUID to link",
        content = @Content(schema = @Schema(example = "{\"catalogResourceId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}")))
    @PutMapping("/datasets/{namespace}/{name}/catalog-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkCatalogResource(
            @Parameter(description = "OpenLineage namespace", example = "snowflake://trading_dw")
            @PathVariable String namespace,
            @Parameter(description = "OpenLineage dataset name", example = "PUBLIC.TRADE_POSITIONS")
            @PathVariable String name,
            @RequestBody Map<String, String> body) {
        lineageDatasetRepository.findByNamespaceAndName(namespace, name).ifPresent(ds -> {
            ds.setCatalogResourceId(UUID.fromString(body.get("catalogResourceId")));
            lineageDatasetRepository.save(ds);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildEdgesForSubgraph(
            String rootNs, String rootName, List<Map<String, Object>> nodes) {
        Set<String> nodeKeys = nodes.stream()
            .map(n -> n.get("namespace") + "/" + n.get("name"))
            .collect(Collectors.toSet());
        nodeKeys.add(rootNs + "/" + rootName);

        return ageGraph.getAllDerivedFromEdges().stream()
            .filter(e -> {
                String from = e.get("from_ns") + "/" + e.get("from_name");
                String to   = e.get("to_ns")   + "/" + e.get("to_name");
                return nodeKeys.contains(from) && nodeKeys.contains(to);
            })
            .toList();
    }
}
