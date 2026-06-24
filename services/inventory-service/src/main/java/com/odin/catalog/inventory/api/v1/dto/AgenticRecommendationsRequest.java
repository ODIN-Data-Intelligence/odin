package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Converged output of the agentic proposer/reviewer loop (produced by ai-service), written onto
 * the model's elements as pending recommendations for the data owner to accept or reject. Each
 * dimension is optional — only the fields the agent proposed for an element are applied.
 */
@Schema(description = "Batch of agentic recommendations to persist as pending suggestions on a model's elements")
public record AgenticRecommendationsRequest(

    @Schema(description = "One recommendation per element")
    List<ElementRecommendation> elements
) {

    public record ElementRecommendation(
        @Schema(description = "Target logical data element UUID") String elementId,
        @Schema(description = "Recommended business description") String description,
        @Schema(description = "Reasoning for the description") String descriptionReasoning,
        @Schema(description = "Recommended classification level",
            allowableValues = {"PUBLIC", "INTERNAL", "CONFIDENTIAL", "HIGH_CONFIDENTIAL"}) String classification,
        @Schema(description = "Reasoning for the classification") String classificationReasoning,
        @Schema(description = "Recommended vocabulary concept mappings") List<VocabConcept> vocabConcepts,
        @Schema(description = "Recommended personal-information flag") Boolean isPersonalInformation,
        @Schema(description = "Recommended direct-identifier flag") Boolean isDirectIdentifier,
        @Schema(description = "Reasoning for the PII indicators") String piiReasoning
    ) {}

    public record VocabConcept(
        String conceptIri,
        String conceptLabel,
        String conceptDefinition,
        String matchType,
        String reasoning
    ) {}
}
