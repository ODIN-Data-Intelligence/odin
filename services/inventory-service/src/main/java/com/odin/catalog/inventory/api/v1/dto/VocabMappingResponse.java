package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "SKOS vocabulary mapping as returned by the API")
public record VocabMappingResponse(

    @Schema(description = "Server-assigned UUID", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "UUID of the vocabulary containing this concept",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID vocabularyId,

    @Schema(description = "Full IRI of the vocabulary concept",
        example = "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount")
    String conceptIri,

    @Schema(description = "Human-readable label of the concept", example = "MonetaryAmount")
    String conceptLabel,

    @Schema(description = "Definition of the concept", example = "A monetary amount is a measure specified in monetary units")
    String conceptDefinition,

    @Schema(description = "SKOS mapping property",
        allowableValues = {"exactMatch", "closeMatch", "relatedMatch", "broadMatch", "narrowMatch"},
        example = "exactMatch")
    String matchType,

    @Schema(description = "Timestamp when the mapping was created", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt

) {}
