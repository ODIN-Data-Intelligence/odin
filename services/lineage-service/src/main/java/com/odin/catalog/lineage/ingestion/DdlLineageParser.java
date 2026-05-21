package com.odin.catalog.lineage.ingestion;

import com.odin.catalog.lineage.infrastructure.age.AgeGraphRepository;
import com.odin.catalog.lineage.infrastructure.jpa.repository.LineageDatasetRepository;
import com.odin.catalog.lineage.infrastructure.jpa.entity.LineageDatasetEntity;
import com.odin.catalog.shared.models.events.HarvestDdlDiscoveredPayload;
import lombok.RequiredArgsConstructor;
import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DdlLineageParser {

    private static final Logger log = LoggerFactory.getLogger(DdlLineageParser.class);

    private final AgeGraphRepository ageGraph;
    private final LineageDatasetRepository datasetRepository;

    @Transactional
    public void process(HarvestDdlDiscoveredPayload payload) {
        if (payload.ddl() == null || payload.ddl().isBlank()) return;

        String targetNamespace = payload.objectNamespace();
        String targetName = payload.objectName();

        List<String[]> sourceRefs = extractSourceTables(payload.ddl(), payload.dialect());
        if (sourceRefs.isEmpty()) {
            log.debug("No source table refs found in DDL for {}.{}", targetNamespace, targetName);
            return;
        }

        // Ensure target dataset node exists
        upsertDataset(targetNamespace, targetName);
        ageGraph.mergeDatasetNode(targetNamespace, targetName);

        for (String[] ref : sourceRefs) {
            String srcNs = ref[0];
            String srcName = ref[1];
            upsertDataset(srcNs, srcName);
            ageGraph.mergeDatasetNode(srcNs, srcName);
            ageGraph.mergeDerivedFromEdge(srcNs, srcName, targetNamespace, targetName);
            log.debug("DDL lineage: {}.{} DERIVED_FROM {}.{}", targetNamespace, targetName, srcNs, srcName);
        }
    }

    private List<String[]> extractSourceTables(String ddl, String dialect) {
        List<String[]> refs = new ArrayList<>();
        String query = extractQueryFromDdl(ddl);
        try {
            SqlParser.Config config = SqlParser.config()
                .withLex(dialectToLex(dialect))
                .withCaseSensitive(false);
            SqlParser parser = SqlParser.create(query, config);
            SqlNode node = parser.parseQuery();
            collectTableRefs(node, refs);
        } catch (Exception e) {
            log.warn("Could not parse DDL with Calcite: {}", e.getMessage());
        }
        return refs;
    }

    private String extractQueryFromDdl(String ddl) {
        if (ddl == null) return "";
        // For CREATE VIEW/TABLE AS SELECT ..., extract the SELECT part
        String upper = ddl.strip().toUpperCase();
        int asIdx = upper.indexOf(" AS ");
        if (asIdx > 0 && (upper.startsWith("CREATE") || upper.startsWith("REPLACE"))) {
            String select = ddl.substring(asIdx + 4).strip();
            // Remove wrapping parentheses if present
            if (select.startsWith("(") && select.endsWith(")")) {
                select = select.substring(1, select.length() - 1).strip();
            }
            return select;
        }
        return ddl;
    }

    private void collectTableRefs(SqlNode node, List<String[]> refs) {
        if (node == null) return;
        if (node instanceof SqlSelect select) {
            // Only traverse the FROM clause, not SELECT list or WHERE
            collectFromRefs(select.getFrom(), refs);
            // Recurse into subqueries in SELECT list
            if (select.getSelectList() != null) {
                for (SqlNode col : select.getSelectList()) {
                    collectTableRefs(col, refs);
                }
            }
        } else if (node instanceof SqlCall call) {
            for (SqlNode operand : call.getOperandList()) {
                collectTableRefs(operand, refs);
            }
        } else if (node instanceof SqlNodeList list) {
            for (SqlNode item : list) {
                collectTableRefs(item, refs);
            }
        }
    }

    private void collectFromRefs(SqlNode from, List<String[]> refs) {
        if (from == null) return;
        if (from instanceof SqlIdentifier id) {
            // Simple table name (no alias)
            if (id.names.size() >= 2) {
                refs.add(new String[]{id.names.get(0), id.names.get(1)});
            } else if (id.names.size() == 1) {
                refs.add(new String[]{"default", id.names.get(0)});
            }
        } else if (from instanceof SqlJoin join) {
            collectFromRefs(join.getLeft(), refs);
            collectFromRefs(join.getRight(), refs);
        } else if (from instanceof SqlCall call) {
            // Handle AS alias: operand 0 is the table, operand 1 is alias
            if (call.getOperator().getName().equalsIgnoreCase("AS") && !call.getOperandList().isEmpty()) {
                collectFromRefs(call.getOperandList().get(0), refs);
            } else {
                // Subquery or other call
                for (SqlNode operand : call.getOperandList()) {
                    collectTableRefs(operand, refs);
                }
            }
        }
    }

    private Lex dialectToLex(String dialect) {
        if (dialect == null) return Lex.MYSQL;
        return switch (dialect.toLowerCase()) {
            case "snowflake" -> Lex.JAVA;
            case "teradata"  -> Lex.SQL_SERVER;
            case "hive"      -> Lex.MYSQL;
            default          -> Lex.JAVA;
        };
    }

    private void upsertDataset(String namespace, String name) {
        datasetRepository.findByNamespaceAndName(namespace, name).orElseGet(() -> {
            LineageDatasetEntity entity = new LineageDatasetEntity();
            entity.setNamespace(namespace);
            entity.setName(name);
            return datasetRepository.save(entity);
        });
    }
}
