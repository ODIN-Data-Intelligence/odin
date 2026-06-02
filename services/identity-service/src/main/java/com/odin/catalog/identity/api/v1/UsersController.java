package com.odin.catalog.identity.api.v1;

import com.odin.catalog.identity.api.v1.dto.UserRequest;
import com.odin.catalog.identity.api.v1.dto.UserResponse;
import com.odin.catalog.identity.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Users", description = "User management — invite, list, and deactivate users; accounts are provisioned in Keycloak")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UsersController {

    private final UserService userService;

    @Operation(summary = "List users",
        description = "Returns a paginated list of users in the tenant organisation.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of users"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions", content = @Content)
    })
    @GetMapping
    public List<UserResponse> list(@PageableDefault(size = 50) Pageable pageable) {
        return userService.list(pageable);
    }

    @Operation(summary = "Get user", description = "Returns a single user by UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public UserResponse get(
            @Parameter(description = "User UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return userService.get(id);
    }

    @Operation(summary = "Get user by Keycloak ID",
        description = "Looks up a user by their Keycloak subject UUID. Used by services that store the Keycloak UUID as an owner reference.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/by-keycloak/{keycloakId}")
    public UserResponse getByKeycloakId(
            @Parameter(description = "Keycloak subject UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable String keycloakId) {
        return userService.getByKeycloakId(keycloakId);
    }

    @Operation(summary = "Invite a user",
        description = "Creates a Keycloak account for the user and sends an invitation email. "
            + "The user must complete registration before they can log in.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User invited and Keycloak account created"),
        @ApiResponse(responseCode = "400", description = "Validation error — email required", content = @Content),
        @ApiResponse(responseCode = "409", description = "A user with this email already exists", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions", content = @Content)
    })
    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse invite(@Valid @RequestBody UserRequest request) {
        return userService.invite(request);
    }

    @Operation(summary = "Update a user",
        description = "Updates the user's name and/or role assignments. Only provided fields are changed.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User updated"),
        @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions", content = @Content)
    })
    @PutMapping("/{id}")
    public UserResponse update(
            @Parameter(description = "User UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody UserRequest request) {
        return userService.update(id, request);
    }

    @Operation(summary = "Activate a user",
        description = "Re-enables a previously deactivated user account.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User activated"),
        @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions", content = @Content)
    })
    @PostMapping("/{id}/activate")
    public UserResponse activate(
            @Parameter(description = "User UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return userService.activate(id);
    }

    @Operation(summary = "Deactivate a user",
        description = "Deactivates the user account and disables the corresponding Keycloak account. "
            + "Active sessions are revoked. This action can be reversed by an admin.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deactivated"),
        @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @Parameter(description = "User UUID to deactivate", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        userService.deactivate(id);
    }
}
