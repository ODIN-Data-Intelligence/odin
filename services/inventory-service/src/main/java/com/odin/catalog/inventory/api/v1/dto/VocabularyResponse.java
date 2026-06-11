package com.odin.catalog.inventory.api.v1.dto;

import com.odin.catalog.inventory.infrastructure.jpa.entity.VocabularyEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Controlled vocabulary registered in ODIN")
public record VocabularyResponse(

    UUID id,
    String name,
    String prefix,
    String baseIri,
    String vocabularyType,
    String description,
    String conceptHints,
    String version,
    String homepage,

    @Schema(description = "True for system-seeded vocabularies (schema.org, FIBO, SKOS). System vocabularies cannot be modified or deleted.")
    boolean isSystem,

    OffsetDateTime createdAt

) {
    public static VocabularyResponse from(VocabularyEntity e) {
        return new VocabularyResponse(
            e.getId(), e.getName(), e.getPrefix(), e.getBaseIri(),
            e.getVocabularyType(), e.getDescription(), e.getConceptHints(),
            e.getVersion(), e.getHomepage(), e.isSystem(), e.getCreatedAt()
        );
    }
}
