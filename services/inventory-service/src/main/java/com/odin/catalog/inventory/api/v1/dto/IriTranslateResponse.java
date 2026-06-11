package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of translating a single concept IRI to a human-readable label")
public record IriTranslateResponse(

    @Schema(description = "The input IRI",
        example = "https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/ClientsAndAccounts/Customer")
    String iri,

    @Schema(description = "Human-readable label resolved for this IRI", example = "Customer")
    String label

) {}
