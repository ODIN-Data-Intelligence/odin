package com.odin.catalog.inventory.api.v1;

import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetVocabularyProfileEntity;
import com.odin.catalog.inventory.infrastructure.jpa.repository.DatasetVocabularyProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Vocabulary Profiles", description = "Associate controlled vocabularies with datasets — drives FIBO vs schema.org concept suggestions in the UI")
@RestController
@RequiredArgsConstructor
public class VocabularyProfilesController {

    private static final Logger log = LoggerFactory.getLogger(VocabularyProfilesController.class);

    private final DatasetVocabularyProfileRepository profileRepository;

    @Operation(summary = "List vocabulary profiles for a dataset",
        description = "Returns the vocabulary associations for a dataset. The primary vocabulary is highlighted and drives AI concept suggestions.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of vocabulary profiles"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/api/v1/datasets/{datasetId}/vocabulary-profiles")
    public List<DatasetVocabularyProfileEntity> list(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId) {
        return profileRepository.findByDatasetId(datasetId);
    }

    @Operation(summary = "Add a vocabulary profile to a dataset",
        description = "Associates a vocabulary with a dataset. Only one vocabulary may be marked as primary per dataset.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Vocabulary profile created"),
        @ApiResponse(responseCode = "409", description = "Vocabulary already associated with this dataset", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Vocabulary association details",
        content = @Content(schema = @Schema(example = """
            {"vocabularyId": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "isPrimary": true, "domainTags": ["financial", "bonds"]}""")))
    @PostMapping("/api/v1/datasets/{datasetId}/vocabulary-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetVocabularyProfileEntity create(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId,
            @RequestBody Map<String, Object> body) {
        UUID vocabId = UUID.fromString((String) body.get("vocabularyId"));
        log.info("action=CREATE_VOCAB_PROFILE datasetId={} vocabId={}", datasetId, vocabId);
        if (profileRepository.existsByDatasetIdAndVocabularyId(datasetId, vocabId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vocabulary profile already exists for this dataset");
        }
        DatasetVocabularyProfileEntity profile = new DatasetVocabularyProfileEntity();
        profile.setDatasetId(datasetId);
        profile.setVocabularyId(vocabId);
        profile.setPrimary(Boolean.TRUE.equals(body.get("isPrimary")));
        if (body.get("domainTags") instanceof List<?> tags) {
            profile.setDomainTags(tags.stream().map(Object::toString).toList());
        }
        return profileRepository.save(profile);
    }

    @Operation(summary = "Remove a vocabulary profile from a dataset",
        description = "Removes the association between a dataset and a vocabulary. Does not affect existing vocabulary mappings on logical elements.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Vocabulary profile removed"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @Transactional
    @DeleteMapping("/api/v1/datasets/{datasetId}/vocabulary-profiles/{vocabId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Dataset UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID datasetId,
            @Parameter(description = "Vocabulary UUID to disassociate", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID vocabId) {
        log.info("action=DELETE_VOCAB_PROFILE datasetId={} vocabId={}", datasetId, vocabId);
        profileRepository.deleteByDatasetIdAndVocabularyId(datasetId, vocabId);
    }
}
