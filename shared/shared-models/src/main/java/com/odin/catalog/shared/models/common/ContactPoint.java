package com.odin.catalog.shared.models.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContactPoint(
    String name,
    String email,
    String url,
    String phone
) {}
