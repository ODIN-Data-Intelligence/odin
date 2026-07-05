package com.odin.catalog.lineage.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Lineage graph response — nodes and directed edges for the requested dataset subgraph")
public record LineageGraphResponse(

    @Schema(description = "Lineage service UUID of the root dataset")
    java.util.UUID rootId,

    @Schema(description = "Namespace of the root dataset", example = "snowflake://trading_dw")
    String rootNamespace,

    @Schema(description = "Name of the root dataset", example = "PUBLIC.TRADE_POSITIONS")
    String rootName,

    @Schema(description = "Direction of traversal from the root dataset",
        allowableValues = {"upstream", "downstream"},
        example = "upstream")
    String direction,

    @Schema(description = "Maximum number of hops traversed from the root", example = "5")
    int depth,

    @Schema(description = "Graph nodes — each node contains id, namespace, name, depth, and optional catalogId")
    List<Map<String, Object>> nodes,

    @Schema(description = "Directed edges in the subgraph — each edge has fromId and toId (lineage dataset UUIDs)")
    List<Map<String, Object>> edges

) {}
