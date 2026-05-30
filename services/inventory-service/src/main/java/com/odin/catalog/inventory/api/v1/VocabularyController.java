package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.application.vocabulary.IriUtils;
import com.odin.catalog.inventory.infrastructure.jpa.entity.VocabularyEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.VocabMappingRepository;
import com.odin.catalog.inventory.infrastructure.jpa.repository.VocabularyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Vocabularies", description = "Controlled vocabulary registry — schema.org, FIBO, SKOS, GeoSPARQL, and custom vocabularies")
@RestController
@RequestMapping("/api/v1/vocabularies")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyRepository vocabularyRepository;
    private final VocabMappingRepository vocabMappingRepository;

    @Operation(summary = "List vocabularies",
        description = "Returns all registered vocabularies. Filter by type to narrow to financial, general, or other categories. "
            + "System vocabularies (schema.org, FIBO, SKOS) are seeded automatically.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of vocabularies"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping
    public List<VocabularyEntity> list(
            @Parameter(description = "Filter by vocabulary type",
                schema = @Schema(allowableValues = {"general", "financial", "healthcare", "geospatial", "custom"}))
            @RequestParam(required = false) String type) {
        if (type != null) {
            return vocabularyRepository.findByVocabularyType(type);
        }
        return vocabularyRepository.findAll();
    }

    @Operation(summary = "Get vocabulary", description = "Returns a single vocabulary by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vocabulary found"),
        @ApiResponse(responseCode = "404", description = "Vocabulary not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public VocabularyEntity get(
            @Parameter(description = "Vocabulary UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return vocabularyRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
    public VocabularyEntity create(@RequestBody VocabularyEntity vocab) {
        vocab.setId(null);
        vocab.setSystem(false);
        return vocabularyRepository.save(vocab);
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
    public Map<String, String> translate(
            @Parameter(description = "Fully-qualified concept IRI to translate",
                example = "https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/ClientsAndAccounts/Customer")
            @RequestParam String iri) {
        String label = vocabMappingRepository.findLabelByConceptIri(iri)
            .orElseGet(() -> IriUtils.humanize(iri));
        return Map.of("iri", iri, "label", label);
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
        if (iris == null || iris.isEmpty()) return Map.of();
        List<String> limited = iris.size() > 200 ? iris.subList(0, 200) : iris;
        Map<String, String> result = new LinkedHashMap<>();
        for (String iri : limited) {
            String label = vocabMappingRepository.findLabelByConceptIri(iri)
                .orElseGet(() -> IriUtils.humanize(iri));
            result.put(iri, label);
        }
        return result;
    }
}
