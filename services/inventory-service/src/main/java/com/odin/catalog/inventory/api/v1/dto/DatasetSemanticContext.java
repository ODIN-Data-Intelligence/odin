package com.odin.catalog.inventory.api.v1.dto;

import java.util.List;
import java.util.UUID;

public record DatasetSemanticContext(
    List<String> semanticTypes,
    List<String> vocabConceptLabels,
    List<String> vocabConceptIris,
    List<String> fiboConcepts,
    List<String> logicalElementNames,
    List<String> logicalTypes,
    List<AcceptedTag> acceptedTags
) {
    public record AcceptedTag(UUID id, String type, String vocabularyIri) {}
}
