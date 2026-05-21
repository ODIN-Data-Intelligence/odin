package com.odin.catalog.harvest.domain.source;

import java.util.List;
import java.util.UUID;

/**
 * Domain record for a configured harvest source.
 * Mapped from harvest_sources + harvest_credentials tables.
 */
public record HarvestSource(
    UUID id,
    UUID tenantId,
    String name,
    String sourceType,           // dcat_http, aws_glue, snowflake, teradata
    String baseUrl,
    String region,
    String databaseName,
    List<String> schemaFilter,
    String credentialRef,
    SourceCredentials credentials,   // decrypted at runtime, never persisted in this form
    Object extraConfig               // parsed from extra_config JSONB
) {}
