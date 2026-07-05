package com.odin.catalog.inventory.api.v1.dto;

import java.util.List;
import java.util.UUID;

public record TermsClassificationRuleResponse(
        UUID id,
        String classification,
        int rank,
        String accessLevel,
        List<String> permissions,
        List<String> prohibitions,
        List<String> obligations,
        List<String> odrlPermissions,
        List<String> odrlProhibitions,
        List<String> odrlDuties
) {}
