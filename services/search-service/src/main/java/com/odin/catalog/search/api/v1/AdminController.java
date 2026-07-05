package com.odin.catalog.search.api.v1;

import com.odin.catalog.search.api.v1.dto.ReindexResponse;
import com.odin.catalog.search.application.ReindexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin", description = "Administrative operations for the search index — requires elevated API key permissions")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ReindexService reindexService;

    @Operation(summary = "Full re-index from inventory-service",
        description = "Fetches all datasets and data products from inventory-service page by page and re-indexes them in OpenSearch. "
            + "For each dataset, also fetches logical models, logical elements, and vocabulary mappings to include in the search document. "
            + "Returns counts of indexed documents. Processing is synchronous and may take several minutes for large catalogs.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Re-index complete — returns counts of indexed entities"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions — elevated key required", content = @Content)
    })
    @PostMapping("/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReindexResponse reindex() {
        return reindexService.reindex();
    }
}
