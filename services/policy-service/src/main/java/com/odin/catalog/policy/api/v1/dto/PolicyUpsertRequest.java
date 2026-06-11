package com.odin.catalog.policy.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PolicyUpsertRequest(

    @Schema(description = "ODRL policy as a JSON-LD string",
        example = "{\"@context\":\"http://www.w3.org/ns/odrl.jsonld\",\"@type\":\"Set\",...}")
    @NotBlank
    String policyJson,

    @Schema(description = "ODRE policy level: A (pure ODRL), B1 (variable injection), B2 (template logic), C (coded function)",
        example = "A", defaultValue = "A")
    String policyLevel
) {}
