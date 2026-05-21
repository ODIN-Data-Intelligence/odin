package com.odin.catalog.identity.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "User as returned by the API")
public record UserResponse(

    @Schema(description = "Server-assigned UUID", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "UUID of the tenant organisation", accessMode = Schema.AccessMode.READ_ONLY,
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID tenantId,

    @Schema(description = "Email address (Keycloak login identity)", example = "jane.smith@example.com")
    String email,

    @Schema(description = "First name", example = "Jane")
    String firstName,

    @Schema(description = "Last name", example = "Smith")
    String lastName,

    @Schema(description = "Whether the user account is active", accessMode = Schema.AccessMode.READ_ONLY,
        example = "true")
    boolean active,

    @Schema(description = "Role names assigned to this user", example = "[\"data-steward\", \"catalog-reader\"]")
    List<String> roles,

    @Schema(description = "Effective permission strings derived from roles",
        example = "[\"dataset:read\", \"dataset:write\", \"catalog:read\"]",
        accessMode = Schema.AccessMode.READ_ONLY)
    List<String> permissions,

    @Schema(description = "Timestamp when the user was invited", accessMode = Schema.AccessMode.READ_ONLY)
    OffsetDateTime createdAt

) {}
