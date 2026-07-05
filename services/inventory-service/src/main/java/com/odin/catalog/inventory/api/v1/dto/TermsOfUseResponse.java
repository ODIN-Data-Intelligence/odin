package com.odin.catalog.inventory.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "ODRL-based terms-of-use derived from element classifications and vocabulary concept mappings")
public record TermsOfUseResponse(

    @Schema(description = "Most restrictive accepted classification across all logical data elements",
        example = "CONFIDENTIAL",
        allowableValues = {"PUBLIC", "INTERNAL", "CONFIDENTIAL", "HIGH_CONFIDENTIAL"})
    String effectiveClassification,

    @Schema(description = "Human-readable access level label",
        example = "RESTRICTED",
        allowableValues = {"OPEN", "INTERNAL_ONLY", "RESTRICTED", "HIGHLY_RESTRICTED"})
    String accessLevel,

    @Schema(description = "Permitted uses of this dataset")
    List<String> permissions,

    @Schema(description = "Prohibited uses of this dataset")
    List<String> prohibitions,

    @Schema(description = "Obligations when using this dataset")
    List<String> obligations,

    @Schema(description = "Regulatory frameworks applicable to this dataset based on vocabulary concept mappings")
    List<String> applicableRegulations,

    @Schema(description = "Full ODRL policy document assembled from all applicable policy pieces")
    Map<String, Object> odrlPolicy,

    @Schema(description = "How this policy was determined",
        example = "derived",
        allowableValues = {"derived", "explicit", "fallback"})
    String policySource,

    @Schema(description = "Details explaining how the recommendation was derived")
    DerivationDetails derivationDetails,

    @Schema(description = "Individual policy pieces that compose the full ODRL policy; null in fallback mode")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<PolicyComponent> policyComponents

) {

    @Schema(description = "Inputs that drove the policy recommendation")
    public record DerivationDetails(

        @Schema(description = "Total elements across published logical models (scope for acceptance check)",
            example = "8")
        int totalPublishedElementCount,

        @Schema(description = "Number of published elements with an accepted classification",
            example = "5")
        int classifiedElementCount,

        @Schema(description = "Number of published elements with at least one controlled vocabulary mapping",
            example = "5")
        int elementsWithVocabCount,

        @Schema(description = "Distinct classification levels found across elements",
            example = "[\"INTERNAL\", \"CONFIDENTIAL\"]")
        List<String> distinctClassifications,

        @Schema(description = "Number of distinct vocabulary concept IRIs mapped to elements",
            example = "12")
        int vocabConceptCount,

        @Schema(description = "Vocabulary prefixes / keyword signals that matched regulatory frameworks",
            example = "[\"fibo-fbc\", \"mifid\"]")
        List<String> matchedSignals,

        @Schema(description = "True when all published elements are both classified and vocabulary-mapped; " +
            "the Accept Policy action is only meaningful when this is true",
            example = "true")
        boolean readyToAccept

    ) {}

    @Schema(description = "A named ODRL policy piece keyed to one policy dimension")
    public record PolicyComponent(

        @Schema(description = "Dimension type", example = "CLASSIFICATION",
            allowableValues = {"CLASSIFICATION", "REGULATION", "CONTRACTUAL", "LOGICAL_TYPE", "CUSTOM"})
        String pieceType,

        @Schema(description = "Dimension value this piece applies to", example = "CONFIDENTIAL")
        String dimensionKey,

        @Schema(description = "Human-readable label", example = "RESTRICTED — CONFIDENTIAL data access rules")
        String label,

        @Schema(description = "Target-free ODRL fragment; target is injected during assembly")
        Map<String, Object> policyFragment

    ) {}
}
