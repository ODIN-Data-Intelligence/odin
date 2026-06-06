package com.odin.catalog.inventory.api.v1.dto;

import java.util.UUID;

public record TermsRegulationRuleResponse(
        UUID id,
        String signalType,
        String pattern,
        String regulationName,
        String signalLabel
) {}
