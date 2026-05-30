package com.odin.catalog.lineage.infrastructure.age;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes Cypher queries against Apache AGE via Spring JDBC.
 *
 * AGE must be loaded per-session (SET search_path = ag_catalog, "$user", public).
 * Spring Boot's Hikari pool does not persist session state, so we issue LOAD + SET
 * in a function that runs before every query.
 */
@Repository
public class AgeGraphRepository {

    private static final Logger log = LoggerFactory.getLogger(AgeGraphRepository.class);
    private static final String GRAPH_NAME = "lineage_graph";

    private final JdbcTemplate jdbcTemplate;

    public AgeGraphRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── Node upsert helpers ───────────────────────────────────────────────────

    public void mergeDatasetNode(String namespace, String name) {
        executeCypher("""
            MERGE (d:Dataset {namespace: %s, name: %s})
            """.formatted(quote(namespace), quote(name)));
    }

    public void mergeJobNode(String namespace, String name) {
        executeCypher("""
            MERGE (j:Job {namespace: %s, name: %s})
            """.formatted(quote(namespace), quote(name)));
    }

    // ── Edge upsert helpers ───────────────────────────────────────────────────

    public void mergeReadByEdge(String datasetNs, String datasetName,
                                 String jobNs, String jobName) {
        executeCypher("""
            MATCH (d:Dataset {namespace: %s, name: %s})
            MATCH (j:Job {namespace: %s, name: %s})
            MERGE (d)-[:READ_BY]->(j)
            """.formatted(quote(datasetNs), quote(datasetName),
                          quote(jobNs), quote(jobName)));
    }

    public void mergeWritesToEdge(String jobNs, String jobName,
                                   String datasetNs, String datasetName) {
        executeCypher("""
            MATCH (j:Job {namespace: %s, name: %s})
            MATCH (d:Dataset {namespace: %s, name: %s})
            MERGE (j)-[:WRITES_TO]->(d)
            """.formatted(quote(jobNs), quote(jobName),
                          quote(datasetNs), quote(datasetName)));
    }

    public void mergeDerivedFromEdge(String inputNs, String inputName,
                                      String outputNs, String outputName) {
        executeCypher("""
            MATCH (src:Dataset {namespace: %s, name: %s})
            MATCH (tgt:Dataset {namespace: %s, name: %s})
            MERGE (tgt)-[:DERIVED_FROM]->(src)
            """.formatted(quote(inputNs), quote(inputName),
                          quote(outputNs), quote(outputName)));
    }

    // ── Lineage traversal ─────────────────────────────────────────────────────

    public List<Map<String, Object>> getUpstreamLineage(String namespace, String name, int depth) {
        if (depth < 1 || depth > 20) {
            throw new IllegalArgumentException("depth must be between 1 and 20, got: " + depth);
        }
        // Edge: output -[:DERIVED_FROM]-> input. Follow outgoing to get ancestors.
        String cypher = """
            MATCH path=(start:Dataset {namespace: %s, name: %s})-[:DERIVED_FROM*1..%d]->(up:Dataset)
            RETURN up.namespace AS namespace, up.name AS name, length(path) AS depth
            ORDER BY depth
            """.formatted(quote(namespace), quote(name), depth);

        return queryCypher(cypher, List.of("namespace", "name", "depth"));
    }

    public List<Map<String, Object>> getDownstreamLineage(String namespace, String name, int depth) {
        if (depth < 1 || depth > 20) {
            throw new IllegalArgumentException("depth must be between 1 and 20, got: " + depth);
        }
        // Edge: output -[:DERIVED_FROM]-> input. Follow incoming to get dependents.
        String cypher = """
            MATCH path=(start:Dataset {namespace: %s, name: %s})<-[:DERIVED_FROM*1..%d]-(down:Dataset)
            RETURN down.namespace AS namespace, down.name AS name, length(path) AS depth
            ORDER BY depth
            """.formatted(quote(namespace), quote(name), depth);

        return queryCypher(cypher, List.of("namespace", "name", "depth"));
    }

    /** Returns all DERIVED_FROM edges in the graph so callers can filter to their subgraph. */
    public List<Map<String, Object>> getAllDerivedFromEdges() {
        String cypher = """
            MATCH (a:Dataset)-[:DERIVED_FROM]->(b:Dataset)
            RETURN a.namespace AS from_ns, a.name AS from_name,
                   b.namespace AS to_ns, b.name AS to_name
            """;
        return queryCypher(cypher, List.of("from_ns", "from_name", "to_ns", "to_name"));
    }

    // ── Internal execution ────────────────────────────────────────────────────

    private void executeCypher(String cypher) {
        String sql = """
            LOAD 'age';
            SET search_path = ag_catalog, "$user", public;
            SELECT * FROM cypher('%s', $$ %s $$) AS (result agtype);
            """.formatted(GRAPH_NAME, cypher);
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.error("AGE Cypher execution failed: {}", e.getMessage());
            log.debug("Failed Cypher: {}", cypher);
            throw e;
        }
    }

    private List<Map<String, Object>> queryCypher(String cypher, List<String> columns) {
        String asClause = columns.stream()
            .map(c -> c + " agtype")
            .reduce((a, b) -> a + ", " + b).orElse("r agtype");

        String selectSql = "SELECT %s FROM cypher('%s', $$ %s $$) AS (%s)"
            .formatted(String.join(", ", columns), GRAPH_NAME, cypher, asClause);

        // Use ConnectionCallback so LOAD + SET + SELECT run on the same JDBC connection.
        // jdbcTemplate.query() uses PreparedStatement which rejects multi-statement strings.
        try {
            return jdbcTemplate.execute((ConnectionCallback<List<Map<String, Object>>>) conn -> {
                List<Map<String, Object>> results = new ArrayList<>();
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("LOAD 'age'");
                    stmt.execute("SET search_path = ag_catalog, \"$user\", public");
                    try (ResultSet rs = stmt.executeQuery(selectSql)) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (String col : columns) {
                                row.put(col, parseAgtypeValue(rs.getString(col)));
                            }
                            results.add(row);
                        }
                    }
                }
                return results;
            });
        } catch (Exception e) {
            log.error("AGE Cypher query failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Strips AGE agtype wrapper to extract the plain value. */
    private Object parseAgtypeValue(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        // String: "\"value\""
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        // Integer or float
        try { return Long.parseLong(raw); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) {}
        return raw;
    }

    private String quote(String value) {
        if (value == null) return "null";
        return "'" + value.replace("'", "\\'") + "'";
    }
}
