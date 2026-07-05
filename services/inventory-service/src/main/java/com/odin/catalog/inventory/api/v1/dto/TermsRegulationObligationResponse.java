package com.odin.catalog.inventory.api.v1.dto;

import java.util.UUID;

public record TermsRegulationObligationResponse(
        UUID id,
        String regulationName,
        String obligation,
        String odrlDuty
) {}
