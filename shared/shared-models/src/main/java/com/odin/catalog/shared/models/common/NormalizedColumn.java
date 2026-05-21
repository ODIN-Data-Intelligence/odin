package com.odin.catalog.shared.models.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Connector-agnostic column description produced by harvest normalizers.
 * Maps to a csvw_columns row in inventory-service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NormalizedColumn(
    String name,
    String datatype,        // XSD type or SQL type string
    boolean required,
    boolean partitionKey,
    String description,
    String propertyUrl,     // optional semantic URL
    int ordinal
) {}
