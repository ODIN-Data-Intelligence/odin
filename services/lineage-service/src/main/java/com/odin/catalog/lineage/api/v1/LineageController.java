package com.odin.catalog.lineage.api.v1;

import com.odin.catalog.lineage.api.v1.dto.LineageGraphResponse;
import com.odin.catalog.lineage.ingestion.OpenLineageHandler;
import com.odin.catalog.lineage.infrastructure.age.AgeGraphRepository;
import com.odin.catalog.lineage.infrastructure.jpa.entity.LineageDatasetEntity;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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

    private static final Logger log = LoggerFactory.getLogger(LineageController.class);

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
        log.info("action=INGEST_RUN_EVENT eventType={}", event.eventType());
        openLineageHandler.handle(event);
    }

    @Operation(summary = "Resolve namespace and name to a lineage dataset UUID",
        description = "Looks up a lineage dataset by OpenLineage namespace and name, returning its lineage service UUID. "
            + "Used by the lineage explorer to resolve user-entered namespace/name to an ID before querying the graph.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lineage dataset UUID resolved",
            content = @Content(schema = @Schema(example = "{\"id\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}"))),
        @ApiResponse(responseCode = "404", description = "No lineage dataset found for this namespace and name", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/datasets/lookup")
    public ResponseEntity<Map<String, String>> lookupDataset(
            @Parameter(description = "OpenLineage namespace", example = "snowflake://trading_dw")
            @RequestParam String namespace,
            @Parameter(description = "OpenLineage dataset name", example = "PUBLIC.TRADE_POSITIONS")
            @RequestParam String name) {
        return lineageDatasetRepository.findByNamespaceAndName(namespace, name)
            .map(ds -> ResponseEntity.ok(Map.of("id", ds.getId().toString())))
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

        return lineageDatasetRepository.findById(id).map(root -> {
            String ns = root.getNamespace();
            String name = root.getName();

            List<Map<String, Object>> rawNodes = "upstream".equalsIgnoreCase(direction)
                ? ageGraph.getUpstreamLineage(ns, name, depth)
                : ageGraph.getDownstreamLineage(ns, name, depth);
            rawNodes.add(0, Map.of("namespace", ns, "name", name, "depth", 0L));

            Map<String, LineageDatasetEntity> entityMap = buildEntityMap(rawNodes);
            List<Map<String, Object>> enrichedNodes = enrichNodes(rawNodes, entityMap);
            List<Map<String, Object>> enrichedEdges = buildEdgesForSubgraph(ns, name, rawNodes, entityMap);

            return ResponseEntity.ok(new LineageGraphResponse(
                id, ns, name, direction, depth, enrichedNodes, enrichedEdges));
        }).orElse(ResponseEntity.notFound().build());
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

        return lineageDatasetRepository.findById(id).map(root -> {
            String ns = root.getNamespace();
            String name = root.getName();

            List<Map<String, Object>> rawNodes = ageGraph.getDownstreamLineage(ns, name, depth);
            rawNodes.add(0, Map.of("namespace", ns, "name", name, "depth", 0L));

            Map<String, LineageDatasetEntity> entityMap = buildEntityMap(rawNodes);
            List<Map<String, Object>> enrichedNodes = enrichNodes(rawNodes, entityMap);
            List<Map<String, Object>> enrichedEdges = buildEdgesForSubgraph(ns, name, rawNodes, entityMap);

            return ResponseEntity.ok(new LineageGraphResponse(
                id, ns, name, "downstream", depth, enrichedNodes, enrichedEdges));
        }).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Resolve lineage identity for a catalog dataset",
        description = "Looks up the lineage UUID, namespace, and name for a given ODIN catalog dataset UUID. "
            + "Used by the consumer UI to fetch lineage for a dataset shown in the drawer.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lineage identity resolved",
            content = @Content(schema = @Schema(example = "{\"id\": \"...\", \"namespace\": \"snowflake://trading_dw\", \"name\": \"PUBLIC.TRADE_POSITIONS\"}"))),
        @ApiResponse(responseCode = "404", description = "No lineage dataset linked to this catalog ID", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/catalog-datasets/{catalogId}/lineage-identity")
    public ResponseEntity<Map<String, String>> getLineageIdentity(
            @Parameter(description = "ODIN catalog dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID catalogId) {
        return lineageDatasetRepository.findByCatalogResourceId(catalogId)
            .map(ds -> ResponseEntity.ok(Map.of(
                "id", ds.getId().toString(),
                "namespace", ds.getNamespace(),
                "name", ds.getName())))
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
        log.info("action=LINK_CATALOG_RESOURCE namespace={} name={}", namespace, name);
        lineageDatasetRepository.findByNamespaceAndName(namespace, name).ifPresent(ds -> {
            ds.setCatalogResourceId(UUID.fromString(body.get("catalogResourceId")));
            lineageDatasetRepository.save(ds);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, LineageDatasetEntity> buildEntityMap(List<Map<String, Object>> nodes) {
        Set<String> namespaces = nodes.stream()
            .map(n -> (String) n.get("namespace"))
            .collect(Collectors.toSet());
        return lineageDatasetRepository.findAllByNamespaceIn(namespaces).stream()
            .collect(Collectors.toMap(
                d -> d.getNamespace() + "/" + d.getName(),
                d -> d));
    }

    private List<Map<String, Object>> enrichNodes(
            List<Map<String, Object>> nodes,
            Map<String, LineageDatasetEntity> entityMap) {
        return nodes.stream().map(n -> {
            String key = n.get("namespace") + "/" + n.get("name");
            LineageDatasetEntity ds = entityMap.get(key);
            Map<String, Object> enriched = new LinkedHashMap<>(n);
            if (ds != null) {
                enriched.put("id", ds.getId().toString());
                if (ds.getCatalogResourceId() != null)
                    enriched.put("catalogId", ds.getCatalogResourceId().toString());
            }
            return enriched;
        }).toList();
    }

    private List<Map<String, Object>> buildEdgesForSubgraph(
            String rootNs, String rootName,
            List<Map<String, Object>> nodes,
            Map<String, LineageDatasetEntity> entityMap) {
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
            .map(e -> {
                LineageDatasetEntity fromDs = entityMap.get(e.get("from_ns") + "/" + e.get("from_name"));
                LineageDatasetEntity toDs   = entityMap.get(e.get("to_ns")   + "/" + e.get("to_name"));
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("fromId", fromDs != null ? fromDs.getId().toString() : "");
                edge.put("toId",   toDs   != null ? toDs.getId().toString()   : "");
                return edge;
            })
            .toList();
    }
}
