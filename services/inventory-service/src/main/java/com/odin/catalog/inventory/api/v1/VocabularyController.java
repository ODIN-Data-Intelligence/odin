package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.api.v1.dto.IriTranslateResponse;
import com.odin.catalog.inventory.api.v1.dto.VocabularyRequest;
import com.odin.catalog.inventory.api.v1.dto.VocabularyResponse;
import com.odin.catalog.inventory.application.vocabulary.VocabularyService;
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
import java.util.Map;
import java.util.UUID;

@Tag(name = "Vocabularies", description = "Controlled vocabulary registry — schema.org, FIBO, SKOS, GeoSPARQL, and custom vocabularies")
@RestController
@RequestMapping("/api/v1/vocabularies")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyService vocabularyService;

    @Operation(summary = "List vocabularies",
        description = "Returns all registered vocabularies. Filter by type to narrow to financial, general, or other categories. "
            + "System vocabularies (schema.org, FIBO, SKOS) are seeded automatically.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of vocabularies"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping
    public List<VocabularyResponse> list(
            @Parameter(description = "Filter by vocabulary type",
                schema = @Schema(allowableValues = {"general", "financial", "healthcare", "geospatial", "custom"}))
            @RequestParam(required = false) String type) {
        return vocabularyService.list(type);
    }

    @Operation(summary = "Get vocabulary", description = "Returns a single vocabulary by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vocabulary found"),
        @ApiResponse(responseCode = "404", description = "Vocabulary not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public VocabularyResponse get(
            @Parameter(description = "Vocabulary UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return vocabularyService.get(id);
    }

    @Operation(summary = "Register a custom vocabulary",
        description = "Registers a new user-defined vocabulary. System vocabularies (isSystem=true) cannot be created via this endpoint. "
            + "The registered vocabulary becomes available for concept mapping on logical data elements.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Vocabulary registered"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VocabularyResponse create(@Valid @RequestBody VocabularyRequest request) {
        return vocabularyService.create(request);
    }

    @Operation(summary = "Update a custom vocabulary",
        description = "Replaces all fields of an existing user-defined vocabulary. System vocabularies (isSystem=true) cannot be updated.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vocabulary updated"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "404", description = "Vocabulary not found", content = @Content),
        @ApiResponse(responseCode = "409", description = "Cannot modify a system vocabulary", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PutMapping("/{id}")
    public VocabularyResponse update(
            @Parameter(description = "Vocabulary UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody VocabularyRequest request) {
        return vocabularyService.update(id, request);
    }

    @Operation(summary = "Delete a custom vocabulary",
        description = "Permanently deletes a user-defined vocabulary. System vocabularies cannot be deleted. "
            + "Existing concept mappings on logical elements that reference this vocabulary are not automatically removed.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Vocabulary not found", content = @Content),
        @ApiResponse(responseCode = "409", description = "Cannot delete a system vocabulary", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Vocabulary UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        vocabularyService.delete(id);
    }

    @Operation(
        summary = "Translate an IRI to a human-readable label",
        description = "Returns the preferred label (skos:prefLabel) stored for the given concept IRI. "
            + "If no stored label exists the IRI local-name is extracted and formatted as a readable phrase. "
            + "Resolution order: (1) conceptLabel stored in any vocab mapping for this IRI, "
            + "(2) humanized IRI fragment (terminal segment after last / or #, camelCase split into words).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Translation result"),
        @ApiResponse(responseCode = "400", description = "Missing iri parameter", content = @Content)
    })
    @GetMapping("/translate")
    public IriTranslateResponse translate(
            @Parameter(description = "Fully-qualified concept IRI to translate",
                example = "https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/ClientsAndAccounts/Customer")
            @RequestParam String iri) {
        return vocabularyService.translate(iri);
    }

    @Operation(
        summary = "Batch-translate IRIs to human-readable labels",
        description = "Translates up to 200 IRIs in a single request. Each IRI is resolved using the same "
            + "priority as the single-IRI endpoint. Returns a map of IRI → label.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Map of IRI to label"),
        @ApiResponse(responseCode = "400", description = "Request body missing or too large", content = @Content)
    })
    @PostMapping("/translate")
    public Map<String, String> translateBatch(@RequestBody List<String> iris) {
        return vocabularyService.translateBatch(iris);
    }
}
