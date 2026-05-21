package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.infrastructure.jpa.entity.CatalogEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.CatalogRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import com.odin.catalog.shared.models.dcat.DcatCatalog;
import com.odin.catalog.shared.models.dcat.DcatDataset;
import com.odin.catalog.shared.models.dcat.DcatResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Catalogs", description = "DCAT Catalog registry — top-level containers that group datasets")
@RestController
@RequestMapping("/api/v1/catalogs")
@RequiredArgsConstructor
public class CatalogsController {

    private final CatalogRepository catalogRepository;
    private final DatasetRepository datasetRepository;

    @Operation(summary = "List catalogs", description = "Returns all catalogs visible to the authenticated tenant.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of catalogs"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping
    public List<CatalogEntity> list() {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        return catalogRepository.findByTenantIdAndIsDeletedFalse(tenantId);
    }

    @Operation(summary = "Get catalog", description = "Returns a single catalog by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Catalog found"),
        @ApiResponse(responseCode = "404", description = "Catalog not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public CatalogEntity get(
            @Parameter(description = "Catalog UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return catalogRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Operation(summary = "Create catalog", description = "Creates a new DCAT catalog for the authenticated tenant.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Catalog created"),
        @ApiResponse(responseCode = "400", description = "Missing required fields", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Catalog fields",
        content = @Content(schema = @Schema(
            example = "{\"title\": \"Enterprise Data Catalog\", \"description\": \"Main catalog\", \"homepage\": \"https://example.com\"}")))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogEntity create(@RequestBody Map<String, Object> body) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        CatalogEntity catalog = new CatalogEntity();
        catalog.setTenantId(tenantId);
        catalog.setTitle((String) body.getOrDefault("title", "Unnamed Catalog"));
        catalog.setDescription((String) body.get("description"));
        catalog.setHomepage((String) body.get("homepage"));
        return catalogRepository.save(catalog);
    }

    @Operation(summary = "Export catalog as DCAT JSON-LD",
        description = "Returns the catalog and all its member datasets serialised as a DCAT 3.0 JSON-LD document "
            + "conforming to https://www.w3.org/TR/vocab-dcat-3/. "
            + "Send `Accept: application/ld+json` to receive the JSON-LD representation.")
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
        CatalogEntity catalog = catalogRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        DcatResource resource = new DcatResource(
            catalog.getId().toString(), "CATALOG", catalog.getIri(),
            catalog.getTenantId().toString(), null,
            catalog.getTitle(), catalog.getDescription(),
            catalog.getLanguage(), catalog.getKeywords(), catalog.getThemes(),
            catalog.getIssued() != null ? catalog.getIssued().toString() : null,
            catalog.getModified() != null ? catalog.getModified().toString() : null,
            catalog.getLicense(), null, null, null, null, null, null,
            catalog.getSourceUri(), null
        );

        List<DcatDataset> datasets = datasetRepository
            .findByCatalogIdAndTenantIdAndIsDeletedFalse(id, catalog.getTenantId())
            .stream()
            .map(ds -> {
                DcatResource dsResource = new DcatResource(
                    ds.getId().toString(), "DATASET", ds.getIri(),
                    ds.getTenantId().toString(),
                    ds.getDomainId() != null ? ds.getDomainId().toString() : null,
                    ds.getTitle(), ds.getDescription(),
                    ds.getLanguage(), ds.getKeywords(), ds.getThemes(),
                    ds.getIssued() != null ? ds.getIssued().toString() : null,
                    ds.getModified() != null ? ds.getModified().toString() : null,
                    ds.getLicense(), null, null, null, null, null, null,
                    ds.getSourceUri(), null
                );
                return new DcatDataset(dsResource, ds.getAccrualPeriodicity(), null,
                    null, null, ds.getVersion(), null, null, null, null);
            })
            .toList();

        return new DcatCatalog(resource, catalog.getHomepage(), null, datasets);
    }
}
