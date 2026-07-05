package com.odin.catalog.inventory.api.v1.dto;

public record RegulationObligationRequest(
        String regulationName,
        String obligation,
        String odrlDuty
) {}
