package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.api.v1.dto.CatalogRequest;
import com.odin.catalog.inventory.api.v1.dto.CatalogResponse;
import com.odin.catalog.inventory.application.catalog.CatalogService;
import com.odin.catalog.shared.models.dcat.DcatCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Catalogs", description = "DCAT Catalog registry — top-level containers that group datasets")
@RestController
@RequestMapping("/api/v1/catalogs")
@RequiredArgsConstructor
public class CatalogsController {

    private final CatalogService catalogService;

    @Operation(summary = "List catalogs", description = "Returns all catalogs visible to the authenticated tenant.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of catalogs"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping
    public List<CatalogResponse> list() {
        return catalogService.list();
    }

    @Operation(summary = "Get catalog", description = "Returns a single catalog by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Catalog found"),
        @ApiResponse(responseCode = "404", description = "Catalog not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public CatalogResponse get(
            @Parameter(description = "Catalog UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return catalogService.get(id);
    }

    @Operation(summary = "Create catalog", description = "Creates a new DCAT catalog for the authenticated tenant.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Catalog created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogResponse create(@Valid @RequestBody CatalogRequest request) {
        return catalogService.create(request);
    }

    @Operation(summary = "Update catalog", description = "Replaces all fields of an existing catalog.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Catalog updated"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Catalog not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PutMapping("/{id}")
    public CatalogResponse update(
            @Parameter(description = "Catalog UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody CatalogRequest request) {
        return catalogService.update(id, request);
    }

    @Operation(summary = "Delete catalog",
        description = "Soft-deletes a catalog. Member datasets are not deleted.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Catalog not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Catalog UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        catalogService.delete(id);
    }

    @Operation(summary = "Export catalog as DCAT JSON-LD",
        description = "Returns the catalog and all its member datasets serialised as a DCAT 3.0 JSON-LD document "
            + "conforming to https://www.w3.org/TR/vocab-dcat-3/.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "DCAT 3.0 JSON-LD catalog document",
            content = @Content(mediaType = "application/ld+json",
                schema = @Schema(implementation = DcatCatalog.class))),
        @ApiResponse(responseCode = "404", description = "Catalog not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping(value = "/{id}/export", produces = "application/ld+json")
    public DcatCatalog export(
            @Parameter(description = "Catalog UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return catalogService.export(id);
    }
}
