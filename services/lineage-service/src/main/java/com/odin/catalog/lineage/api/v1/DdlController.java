package com.odin.catalog.lineage.api.v1;

import com.odin.catalog.lineage.api.v1.dto.DdlSubmitRequest;
import com.odin.catalog.lineage.ingestion.DdlLineageParser;
import com.odin.catalog.shared.models.events.HarvestDdlDiscoveredPayload;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "DDL", description = "DDL-based lineage extraction — parse CREATE VIEW / TABLE AS SELECT statements to derive DERIVED_FROM edges in the graph")
@RestController
@RequestMapping("/api/v1/ddl")
@RequiredArgsConstructor
public class DdlController {

    private static final Logger log = LoggerFactory.getLogger(DdlController.class);

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
    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void submit(@Valid @RequestBody DdlSubmitRequest request) {
        String outputName = request.outputName() != null
            ? request.outputName()
            : extractObjectName(request.ddl());
        log.info("action=SUBMIT_DDL outputName={}", outputName);
        var payload = new HarvestDdlDiscoveredPayload(
            null, null, null, "VIEW",
            request.namespace() != null ? request.namespace() : "default",
            outputName,
            request.dialect() != null ? request.dialect() : "ANSI",
            request.ddl()
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
