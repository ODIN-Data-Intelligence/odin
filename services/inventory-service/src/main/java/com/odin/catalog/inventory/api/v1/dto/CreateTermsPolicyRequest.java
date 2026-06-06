package com.odin.catalog.inventory.api.v1.dto;

public record CreateTermsPolicyRequest(
        String name,
        String description
) {}
