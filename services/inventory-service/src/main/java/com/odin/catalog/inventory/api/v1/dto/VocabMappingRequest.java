package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for adding a SKOS vocabulary mapping to a logical data element")
public record VocabMappingRequest(

    @Schema(description = "UUID of the vocabulary containing the target concept",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull UUID vocabularyId,

    @Schema(description = "Full IRI of the vocabulary concept",
        example = "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount")
    @NotBlank String conceptIri,

    @Schema(description = "Human-readable label of the concept", example = "MonetaryAmount")
    String conceptLabel,

    @Schema(description = "Definition of the concept sourced from the vocabulary",
        example = "A monetary amount is a measure that is an amount of money specified in monetary units")
    String conceptDefinition,

    @Schema(description = "SKOS mapping property expressing the relationship strength",
        allowableValues = {"exactMatch", "closeMatch", "relatedMatch", "broadMatch", "narrowMatch"},
        example = "exactMatch")
    String matchType

) {}
