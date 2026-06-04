package com.odin.catalog.identity.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin client for the Keycloak Admin REST API.
 * Uses the identity-service service-account credentials (client_credentials grant).
 */
@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private final RestClient restClient;
    private final String realm;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminClient(
            RestClient.Builder builder,
            @Value("${keycloak.server-url:http://keycloak:8180}") String serverUrl,
            @Value("${keycloak.realm:datacatalog}") String realm,
            @Value("${keycloak.client-id:identity-service}") String clientId,
            @Value("${keycloak.client-secret:change-me-in-production}") String clientSecret) {
        this.realm        = realm;
        this.clientId     = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl     = serverUrl + "/realms/master/protocol/openid-connect/token";
        this.restClient   = builder.baseUrl(serverUrl).build();
    }

    // ── Token ────────────────────────────────────────────────────────────────

    private String adminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        TokenResponse resp = restClient.post()
            .uri("/realms/{realm}/protocol/openid-connect/token", realm)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse.class);
        if (resp == null || resp.accessToken() == null) {
            throw new KeycloakException("Failed to obtain Keycloak admin token");
        }
        return resp.accessToken();
    }

    // ── User operations ───────────────────────────────────────────────────────

    public List<KeycloakUser> listUsers() {
        String token = adminToken();
        List<KeycloakUser> users = restClient.get()
            .uri("/admin/realms/{realm}/users?briefRepresentation=false&max=200", realm)
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<List<KeycloakUser>>() {});
        return users == null ? List.of() : users.stream()
            .filter(u -> u.username() != null && !u.username().startsWith("service-account-"))
            .toList();
    }

    /** Fetches a single Keycloak user by UUID. Returns empty if not found or admin access fails. */
    public java.util.Optional<KeycloakUser> getUserById(String keycloakUserId) {
        try {
            String token = adminToken();
            KeycloakUser user = restClient.get()
                .uri("/admin/realms/{realm}/users/{id}", realm, keycloakUserId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(KeycloakUser.class);
            return java.util.Optional.ofNullable(user);
        } catch (Exception e) {
            log.warn("action=KEYCLOAK_GET_USER_FAILED userId={} error={}", keycloakUserId, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    public List<RealmRole> getUserRealmRoles(String keycloakUserId) {
        String token = adminToken();
        try {
            List<RealmRole> roles = restClient.get()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakUserId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<RealmRole>>() {});
            return roles == null ? List.of() : roles;
        } catch (Exception e) {
            log.warn("action=KEYCLOAK_GET_ROLES_FAILED userId={} error={}", keycloakUserId, e.getMessage());
            return List.of();
        }
    }

    /** Creates the user and returns the new Keycloak user ID from the Location header. */
    public String createUser(String email, String firstName, String lastName,
                             List<String> roles, List<String> permissions,
                             String tenantId) {
        String token = adminToken();
        Map<String, Object> body = Map.of(
            "username",      email,
            "email",         email,
            "firstName",     firstName != null ? firstName : "",
            "lastName",      lastName  != null ? lastName  : "",
            "enabled",       true,
            "emailVerified", false,
            "requiredActions", List.of("UPDATE_PASSWORD"),
            "attributes", Map.of(
                "tenant_id",   List.of(tenantId),
                "permissions", permissions != null ? permissions : List.of()
            )
        );

        var response = restClient.post()
            .uri("/admin/realms/{realm}/users", realm)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();

        String location = response.getHeaders().getFirst("Location");
        if (location == null) throw new KeycloakException("Keycloak did not return Location for created user");
        String keycloakUserId = location.substring(location.lastIndexOf('/') + 1);

        // Assign realm roles
        if (roles != null && !roles.isEmpty()) {
            assignRealmRoles(keycloakUserId, roles, token);
        }

        log.info("action=KEYCLOAK_USER_CREATED keycloakUserId={} email={}", keycloakUserId, email);
        return keycloakUserId;
    }

    public void setEnabled(String keycloakUserId, boolean enabled) {
        String token = adminToken();
        restClient.put()
            .uri("/admin/realms/{realm}/users/{id}", realm, keycloakUserId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("enabled", enabled))
            .retrieve()
            .toBodilessEntity();
        log.info("action=KEYCLOAK_USER_ENABLED keycloakUserId={} enabled={}", keycloakUserId, enabled);
    }

    public void updateUser(String keycloakUserId, String firstName, String lastName,
                           List<String> roles, List<String> permissions, String tenantId) {
        String token = adminToken();
        restClient.put()
            .uri("/admin/realms/{realm}/users/{id}", realm, keycloakUserId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "firstName",  firstName != null ? firstName : "",
                "lastName",   lastName  != null ? lastName  : "",
                "attributes", Map.of(
                    "tenant_id",   List.of(tenantId),
                    "permissions", permissions != null ? permissions : List.of()
                )
            ))
            .retrieve()
            .toBodilessEntity();

        if (roles != null) assignRealmRoles(keycloakUserId, roles, token);
        log.info("action=KEYCLOAK_USER_UPDATED keycloakUserId={}", keycloakUserId);
    }

    // ── Role assignment ───────────────────────────────────────────────────────

    private void assignRealmRoles(String keycloakUserId, List<String> roleNames, String token) {
        // Fetch all realm roles once and filter
        List<RealmRole> allRoles = restClient.get()
            .uri("/admin/realms/{realm}/roles", realm)
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<List<RealmRole>>() {});
        if (allRoles == null) return;

        List<RealmRole> toAssign = allRoles.stream()
            .filter(r -> roleNames.contains(r.name()))
            .toList();

        if (!toAssign.isEmpty()) {
            // Remove existing realm roles first, then assign the new set
            try {
                List<RealmRole> existing = restClient.get()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakUserId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<RealmRole>>() {});
                if (existing != null && !existing.isEmpty()) {
                    restClient.method(org.springframework.http.HttpMethod.DELETE)
                        .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakUserId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(existing)
                        .retrieve()
                        .toBodilessEntity();
                }
            } catch (HttpClientErrorException ignored) {}

            restClient.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakUserId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toAssign)
                .retrieve()
                .toBodilessEntity();
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeycloakUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        Map<String, List<String>> attributes
    ) {
        public String tenantId() {
            if (attributes == null) return null;
            List<String> v = attributes.get("tenant_id");
            return (v != null && !v.isEmpty()) ? v.get(0) : null;
        }
        public List<String> permissions() {
            if (attributes == null) return List.of();
            List<String> v = attributes.get("permissions");
            return v != null ? v : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RealmRole(String id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    public static class KeycloakException extends RuntimeException {
        public KeycloakException(String msg) { super(msg); }
    }
}
