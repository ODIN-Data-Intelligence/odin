package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Logical data element as returned by the API")
public record LogicalDataElementResponse(

    @Schema(description = "Server-assigned UUID", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "UUID of the parent logical model", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID logicalModelId,

    @Schema(description = "Business name of this element", example = "Trade Amount")
    String name,

    @Schema(description = "Display label", example = "Trade Amount (USD)")
    String label,

    @Schema(description = "Description of this element", example = "Notional value in settlement currency")
    String description,

    @Schema(description = "Semantic type",
        allowableValues = {"MonetaryAmount", "Currency", "Identifier", "Date", "DateTime", "Party",
            "Quantity", "Percentage", "Flag", "Text", "Code", "URI"},
        example = "MonetaryAmount")
    String logicalType,

    @Schema(description = "Display order within the model (1-based)", example = "1")
    int ordinal,

    @Schema(description = "Whether this element is mandatory", example = "true")
    boolean isRequired,

    @Schema(description = "Whether this element is part of the logical key", example = "false")
    boolean isIdentifier,

    @Schema(description = "Whether null values are permitted", example = "false")
    boolean isNullable,

    @Schema(description = "UUIDs of bound physical columns (csvw_columns rows). Empty until harvest or manual bind.")
    List<UUID> physicalColumnIds,

    @Schema(description = "SKOS vocabulary mappings attached to this element")
    List<VocabMappingResponse> vocabMappings,

    @Schema(description = "Creation timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt,

    @Schema(description = "Last modification timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime updatedAt,

    @Schema(description = "Accepted data classification level",
        allowableValues = {"PUBLIC", "INTERNAL", "CONFIDENTIAL", "HIGH_CONFIDENTIAL"})
    String classification,

    @Schema(description = "AI-recommended classification pending review",
        allowableValues = {"PUBLIC", "INTERNAL", "CONFIDENTIAL", "HIGH_CONFIDENTIAL"})
    String recommendedClassification,

    @Schema(description = "One-sentence reasoning from the AI for the recommendation")
    String classificationReasoning,

    @Schema(description = "When the AI recommendation was generated", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime classificationRecommendedAt

) {}
