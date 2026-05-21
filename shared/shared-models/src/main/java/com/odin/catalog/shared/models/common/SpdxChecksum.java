package com.odin.catalog.shared.models.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpdxChecksum(
    String algorithm,   // e.g. "SHA-256", "MD5"
    String checksumValue
) {}
