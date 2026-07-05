package com.odin.catalog.inventory.api.v1.dto;

public record UpdateTermsPolicyRequest(
        String name,
        String description
) {}
