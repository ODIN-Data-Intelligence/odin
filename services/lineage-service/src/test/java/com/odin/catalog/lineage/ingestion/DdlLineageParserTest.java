package com.odin.catalog.lineage.ingestion;

import com.odin.catalog.lineage.infrastructure.age.AgeGraphRepository;
import com.odin.catalog.lineage.infrastructure.jpa.entity.LineageDatasetEntity;
import com.odin.catalog.lineage.infrastructure.jpa.repository.LineageDatasetRepository;
import com.odin.catalog.shared.models.events.HarvestDdlDiscoveredPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DdlLineageParserTest {

    // HarvestDdlDiscoveredPayload(runId, sourceId, sourceType, objectType,
    //                              objectNamespace, objectName, dialect, ddl)

    @Mock AgeGraphRepository ageGraph;
    @Mock LineageDatasetRepository datasetRepository;

    @InjectMocks DdlLineageParser parser;

    // ── empty / blank / null DDL ──────────────────────────────────────────

    @Test
    void process_nullDdl_doesNothing() {
        parser.process(payload("analytics", "v_trades", null, "default"));
        verifyNoInteractions(ageGraph, datasetRepository);
    }

    @Test
    void process_blankDdl_doesNothing() {
        parser.process(payload("analytics", "v_trades", "   ", "default"));
        verifyNoInteractions(ageGraph, datasetRepository);
    }

    // ── single-table view ─────────────────────────────────────────────────

    @Test
    void process_createViewSingleTable_extractsOneEdge() {
        String ddl = "CREATE VIEW analytics.v_trades AS SELECT * FROM trading.executed_trades";

        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.empty());
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        parser.process(payload("analytics", "v_trades", ddl, "default"));

        verify(ageGraph).mergeDatasetNode("analytics", "v_trades");
        verify(ageGraph).mergeDatasetNode("trading", "executed_trades");
        verify(ageGraph).mergeDerivedFromEdge("trading", "executed_trades", "analytics", "v_trades");
    }

    // ── two-table join view ───────────────────────────────────────────────

    @Test
    void process_createViewJoin_extractsTwoEdges() {
        String ddl = "CREATE VIEW risk.v_exposure AS " +
                     "SELECT t.id, c.name FROM trading.trades t " +
                     "JOIN reference.counterparties c ON t.cpty_id = c.id";

        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.empty());
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        parser.process(payload("risk", "v_exposure", ddl, "default"));

        verify(ageGraph).mergeDerivedFromEdge("trading", "trades", "risk", "v_exposure");
        verify(ageGraph).mergeDerivedFromEdge("reference", "counterparties", "risk", "v_exposure");
    }

    // ── unqualified table name ────────────────────────────────────────────

    @Test
    void process_unqualifiedTableName_usesDefaultNamespace() {
        String ddl = "CREATE VIEW analytics.v_summary AS SELECT * FROM raw_data";

        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.empty());
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        parser.process(payload("analytics", "v_summary", ddl, "default"));

        verify(ageGraph).mergeDerivedFromEdge("default", "raw_data", "analytics", "v_summary");
    }

    // ── wrapped-parentheses SELECT ────────────────────────────────────────

    @Test
    void process_createViewWithParentheses_stripsAndParses() {
        String ddl = "CREATE VIEW ns.v AS (SELECT * FROM ns.src_table)";

        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.empty());
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        parser.process(payload("ns", "v", ddl, "default"));

        verify(ageGraph).mergeDerivedFromEdge("ns", "src_table", "ns", "v");
    }

    // ── existing dataset reused ───────────────────────────────────────────

    @Test
    void process_existingSourceDataset_notInsertedAgain() {
        String ddl = "CREATE VIEW ns.v AS SELECT * FROM ns.src";

        LineageDatasetEntity existing = new LineageDatasetEntity();
        existing.setNamespace("ns");
        existing.setName("src");

        when(datasetRepository.findByNamespaceAndName(eq("ns"), eq("src"))).thenReturn(Optional.of(existing));
        when(datasetRepository.findByNamespaceAndName(eq("ns"), eq("v"))).thenReturn(Optional.empty());
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        parser.process(payload("ns", "v", ddl, "default"));

        // save called only for the new target "v"
        verify(datasetRepository, times(1)).save(any());
    }

    // ── unparseable DDL doesn't throw ─────────────────────────────────────

    @Test
    void process_unparseableDdl_doesNotThrow() {
        String ddl = "CREATE VIEW analytics.v_broken AS THIS IS NOT VALID SQL !!!";
        assertThatCode(() -> parser.process(payload("analytics", "v_broken", ddl, "default")))
            .doesNotThrowAnyException();
        verify(ageGraph, never()).mergeDerivedFromEdge(any(), any(), any(), any());
    }

    // ── plain SELECT without table refs ──────────────────────────────────

    @Test
    void process_selectWithoutFrom_doesNotMergeEdges() {
        // Calcite may parse "SELECT 1" but finds no FROM clause
        String ddl = "CREATE VIEW ns.v AS SELECT 1 AS val";
        assertThatCode(() -> parser.process(payload("ns", "v", ddl, "default")))
            .doesNotThrowAnyException();
        verify(ageGraph, never()).mergeDerivedFromEdge(any(), any(), any(), any());
    }

    // ── dialect selection ─────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"snowflake", "teradata", "hive", "default"})
    void process_variousDialects_doesNotThrow(String dialect) {
        String ddl = "CREATE VIEW analytics.v AS SELECT * FROM trading.src";

        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.empty());
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> parser.process(payload("analytics", "v", ddl, dialect)))
            .doesNotThrowAnyException();
    }

    @Test
    void process_nullDialect_doesNotThrow() {
        String ddl = "CREATE VIEW analytics.v AS SELECT * FROM trading.src";

        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.empty());
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> parser.process(payload("analytics", "v", ddl, null)))
            .doesNotThrowAnyException();
    }

    // ── non-CREATE DDL ────────────────────────────────────────────────────

    @Test
    void process_plainSelectWithoutCreate_attemptsParseGracefully() {
        // DDL without "CREATE ... AS" prefix — no " AS " found, so whole string is parsed as-is
        String ddl = "SELECT t.id FROM trading.trades t JOIN reference.cpty c ON t.id = c.id";

        when(datasetRepository.findByNamespaceAndName(any(), any())).thenReturn(Optional.empty());
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> parser.process(payload("analytics", "v_report", ddl, "default")))
            .doesNotThrowAnyException();
    }

    // ── fixture ───────────────────────────────────────────────────────────

    private HarvestDdlDiscoveredPayload payload(String namespace, String name, String ddl, String dialect) {
        return new HarvestDdlDiscoveredPayload(
            "run-1", "src-1", "snowflake", "VIEW",
            namespace, name, dialect, ddl
        );
    }
}
