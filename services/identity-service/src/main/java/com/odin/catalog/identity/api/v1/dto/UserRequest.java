package com.odin.catalog.identity.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(description = "Request body for inviting a new user to the organisation")
public record UserRequest(

    @Schema(description = "Email address — used as the Keycloak login identity", example = "jane.smith@example.com")
    @NotBlank @Email String email,

    @Schema(description = "User's first name", example = "Jane")
    String firstName,

    @Schema(description = "User's last name", example = "Smith")
    String lastName,

    @Schema(description = "Initial role assignments for this user",
        example = "[\"data-steward\", \"catalog-reader\"]")
    List<String> roles

) {}
