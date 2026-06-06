package com.odin.catalog.inventory.api.v1.dto;

public record RegulationRuleRequest(
        String signalType,
        String pattern,
        String regulationName,
        String signalLabel
) {}
