package com.odin.catalog.lineage.application;

import com.odin.catalog.lineage.api.v1.dto.LineageGraphResponse;
import com.odin.catalog.lineage.infrastructure.age.AgeGraphRepository;
import com.odin.catalog.lineage.infrastructure.jpa.entity.LineageDatasetEntity;
import com.odin.catalog.lineage.infrastructure.jpa.repository.LineageDatasetRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LineageGraphService {

    private static final Logger log = LoggerFactory.getLogger(LineageGraphService.class);

    private final AgeGraphRepository ageGraph;
    private final LineageDatasetRepository lineageDatasetRepository;

    public Optional<LineageGraphResponse> buildGraph(UUID id, String direction, int depth) {
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

            return new LineageGraphResponse(id, ns, name, direction, depth, enrichedNodes, enrichedEdges);
        });
    }

    public Optional<LineageGraphResponse> buildImpact(UUID id, int depth) {
        return lineageDatasetRepository.findById(id).map(root -> {
            String ns = root.getNamespace();
            String name = root.getName();

            List<Map<String, Object>> rawNodes = ageGraph.getDownstreamLineage(ns, name, depth);
            rawNodes.add(0, Map.of("namespace", ns, "name", name, "depth", 0L));

            Map<String, LineageDatasetEntity> entityMap = buildEntityMap(rawNodes);
            List<Map<String, Object>> enrichedNodes = enrichNodes(rawNodes, entityMap);
            List<Map<String, Object>> enrichedEdges = buildEdgesForSubgraph(ns, name, rawNodes, entityMap);

            return new LineageGraphResponse(id, ns, name, "downstream", depth, enrichedNodes, enrichedEdges);
        });
    }

    public void linkCatalogResource(String namespace, String name, UUID catalogResourceId) {
        log.info("action=LINK_CATALOG_RESOURCE namespace={} name={}", namespace, name);
        lineageDatasetRepository.findByNamespaceAndName(namespace, name).ifPresent(ds -> {
            ds.setCatalogResourceId(catalogResourceId);
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
