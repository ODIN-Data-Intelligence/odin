package com.odin.catalog.lineage.api.v1;

import com.odin.catalog.lineage.ingestion.DdlLineageParser;
import com.odin.catalog.shared.models.events.HarvestDdlDiscoveredPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "DDL", description = "DDL-based lineage extraction — parse CREATE VIEW / TABLE AS SELECT statements to derive DERIVED_FROM edges in the graph")
@RestController
@RequestMapping("/api/v1/ddl")
@RequiredArgsConstructor
public class DdlController {

    private final DdlLineageParser ddlParser;

    @Operation(summary = "Submit DDL for lineage extraction",
        description = "Parses the provided DDL statement using Apache Calcite and extracts table-level lineage. "
            + "A DERIVED_FROM edge is created in the Apache AGE graph for each source table referenced in the DDL. "
            + "Supported: CREATE VIEW … AS SELECT, CREATE TABLE … AS SELECT. "
            + "Supported dialects: ANSI, Snowflake, Teradata, Hive. "
            + "Processing is asynchronous — the endpoint returns 202 immediately.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "DDL accepted for processing"),
        @ApiResponse(responseCode = "400", description = "Missing required 'ddl' field", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "DDL statement and optional metadata",
        required = true,
        content = @Content(schema = @Schema(example = """
            {
              "ddl": "CREATE VIEW risk.daily_pnl AS SELECT t.trade_id, t.pnl FROM trades.positions t",
              "namespace": "snowflake://trading_dw",
              "outputName": "daily_pnl",
              "dialect": "ANSI"
            }""")))
    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void submit(@RequestBody Map<String, String> body) {
        String ddl = body.get("ddl");
        String outputName = body.containsKey("outputName")
            ? body.get("outputName")
            : extractObjectName(ddl);
        var payload = new HarvestDdlDiscoveredPayload(
            null, null, null, "VIEW",
            body.getOrDefault("namespace", "default"),
            outputName,
            body.getOrDefault("dialect", "ANSI"),
            ddl
        );
        ddlParser.process(payload);
    }

    private String extractObjectName(String ddl) {
        if (ddl == null) return "unknown";
        String[] tokens = ddl.strip().split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            String t = tokens[i].toUpperCase();
            if (t.equals("VIEW") || t.equals("TABLE")) {
                String name = tokens[i + 1].replaceAll("[`\"\\[\\]]", "");
                int dot = name.lastIndexOf('.');
                return dot >= 0 ? name.substring(dot + 1) : name;
            }
        }
        return "unknown";
    }
}
