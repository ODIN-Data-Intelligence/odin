package com.odin.catalog.inventory.application.dataset;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ActiveTermsPolicy(
        UUID policySetId,
        String policyName,
        Map<String, ClassificationRule> classificationRules,
        List<RegulationDetectionRule> regulationRules,
        List<RegulationObligation> regulationObligations
) {
    public record ClassificationRule(
            int rank,
            String accessLevel,
            List<String> permissions,
            List<String> prohibitions,
            List<String> obligations,
            List<String> odrlPermissions,
            List<String> odrlProhibitions,
            List<String> odrlDuties
    ) {}

    public record RegulationDetectionRule(
            String signalType,
            String pattern,
            String regulationName,
            String signalLabel
    ) {}

    public record RegulationObligation(
            String regulationName,
            String obligation,
            String odrlDuty
    ) {}
}
