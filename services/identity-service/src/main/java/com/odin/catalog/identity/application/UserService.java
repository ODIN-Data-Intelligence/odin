package com.odin.catalog.identity.application;

import com.odin.catalog.identity.api.v1.dto.UserRequest;
import com.odin.catalog.identity.api.v1.dto.UserResponse;
import com.odin.catalog.identity.infrastructure.jpa.entity.UserEntity;
import com.odin.catalog.identity.infrastructure.jpa.repository.UserRepository;
import com.odin.catalog.identity.keycloak.KeycloakAdminClient;
import com.odin.catalog.identity.keycloak.KeycloakAdminClient.KeycloakUser;
import com.odin.catalog.identity.keycloak.KeycloakAdminClient.RealmRole;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    private final UserRepository       userRepository;
    private final KeycloakAdminClient  keycloak;

    // ── List ─────────────────────────────────────────────────────────────────

    @Transactional
    public List<UserResponse> list(Pageable pageable) {
        String tenantId = tenantId();

        // Pull all users from Keycloak (source of truth for existence / enabled status)
        List<KeycloakUser> kcUsers;
        try {
            kcUsers = keycloak.listUsers();
        } catch (Exception e) {
            log.warn("action=KEYCLOAK_LIST_FAILED error={} — falling back to local DB", e.getMessage());
            return userRepository.findByTenantId(UUID.fromString(tenantId), pageable)
                .map(this::toResponse).toList();
        }

        // Build local DB index by keycloak_user_id for fast merge
        Map<String, UserEntity> byKeycloakId = userRepository.findAll().stream()
            .filter(u -> u.getKeycloakUserId() != null)
            .collect(Collectors.toMap(UserEntity::getKeycloakUserId, Function.identity()));
        // Also index by email for users seeded without keycloak_user_id
        Map<String, UserEntity> byEmail = userRepository.findAll().stream()
            .collect(Collectors.toMap(UserEntity::getEmail, Function.identity(), (a, b) -> a));

        return kcUsers.stream()
            .filter(u -> u.email() != null)
            .map(u -> {
                // Resolve local entity (prefer keycloak_user_id, fall back to email)
                UserEntity local = byKeycloakId.getOrDefault(u.id(), byEmail.get(u.email()));

                // Lazy-upsert into local DB so it stays in sync
                local = syncToLocal(u, local, tenantId);

                List<String> roles = local.getRoles() != null && !local.getRoles().isEmpty()
                    ? local.getRoles()
                    : keycloak.getUserRealmRoles(u.id()).stream().map(RealmRole::name).toList();

                return new UserResponse(
                    local.getId(), local.getTenantId(), u.email(),
                    u.firstName(), u.lastName(), u.enabled(),
                    roles, local.getPermissions(), local.getCreatedAt(),
                    u.id()
                );
            })
            .toList();
    }

    // ── Get ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public UserResponse getByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakUserId(keycloakId)
            .map(this::toResponse)
            .orElseThrow(() -> new NoSuchElementException("User not found for Keycloak ID: " + keycloakId));
    }

    // ── Invite ───────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse getByKeycloakId(String keycloakId) {
        // Fast path: already synced locally
        Optional<UserEntity> local = userRepository.findByKeycloakUserId(keycloakId);
        if (local.isPresent()) return toResponse(local.get());

        // Fallback: look up the user in Keycloak directly and match by email
        return keycloak.getUserById(keycloakId)
            .filter(kc -> kc.email() != null)
            .flatMap(kc -> userRepository.findByEmail(kc.email())
                .map(entity -> {
                    entity.setKeycloakUserId(keycloakId);
                    if (entity.getFirstName() == null && kc.firstName() != null)
                        entity.setFirstName(kc.firstName());
                    if (entity.getLastName() == null && kc.lastName() != null)
                        entity.setLastName(kc.lastName());
                    return toResponse(userRepository.save(entity));
                }))
            .orElseThrow(() -> new NoSuchElementException("User not found for Keycloak ID: " + keycloakId));
    }

    // ── Invite ───────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse invite(UserRequest request) {
        String tenantId = tenantId();
        List<String> permissions = derivePermissions(request.roles());

        // 1. Create in Keycloak
        String keycloakUserId = null;
        try {
            keycloakUserId = keycloak.createUser(
                request.email(), request.firstName(), request.lastName(),
                request.roles(), permissions, tenantId);
        } catch (Exception e) {
            log.warn("action=KEYCLOAK_CREATE_USER_FAILED email={} error={}", request.email(), e.getMessage());
            // Continue — store locally even if Keycloak is unavailable
        }

        // 2. Upsert in local DB
        Optional<UserEntity> existing = userRepository.findByEmail(request.email());
        UserEntity entity = existing.orElse(new UserEntity());
        entity.setTenantId(UUID.fromString(tenantId));
        entity.setEmail(request.email());
        entity.setFirstName(request.firstName());
        entity.setLastName(request.lastName());
        entity.setRoles(request.roles());
        entity.setPermissions(permissions);
        entity.setActive(true);
        if (keycloakUserId != null) entity.setKeycloakUserId(keycloakUserId);

        UserResponse result = toResponse(userRepository.save(entity));
        log.info("action=USER_INVITED userId={} tenantId={} email={}", result.id(), tenantId, request.email());
        return result;
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        UserEntity entity = findOrThrow(id);
        List<String> permissions = derivePermissions(request.roles());

        if (entity.getKeycloakUserId() != null) {
            try {
                keycloak.updateUser(entity.getKeycloakUserId(),
                    request.firstName(), request.lastName(), request.roles(), permissions, tenantId());
            } catch (Exception e) {
                log.warn("action=KEYCLOAK_UPDATE_FAILED userId={} error={}", id, e.getMessage());
            }
        }

        if (request.firstName() != null) entity.setFirstName(request.firstName());
        if (request.lastName()  != null) entity.setLastName(request.lastName());
        if (request.roles()     != null) {
            entity.setRoles(request.roles());
            entity.setPermissions(permissions);
        }
        UserResponse result = toResponse(userRepository.save(entity));
        log.info("action=USER_UPDATED userId={} tenantId={}", id, entity.getTenantId());
        return result;
    }

    // ── Deactivate / Activate ─────────────────────────────────────────────────

    @Transactional
    public void deactivate(UUID id) {
        UserEntity entity = findOrThrow(id);
        if (entity.getKeycloakUserId() != null) {
            try {
                keycloak.setEnabled(entity.getKeycloakUserId(), false);
            } catch (Exception e) {
                log.warn("action=KEYCLOAK_DISABLE_FAILED userId={} error={}", id, e.getMessage());
            }
        }
        entity.setActive(false);
        userRepository.save(entity);
        log.info("action=USER_DEACTIVATED userId={} tenantId={}", id, entity.getTenantId());
    }

    @Transactional
    public UserResponse activate(UUID id) {
        UserEntity entity = findOrThrow(id);
        if (entity.getKeycloakUserId() != null) {
            try {
                keycloak.setEnabled(entity.getKeycloakUserId(), true);
            } catch (Exception e) {
                log.warn("action=KEYCLOAK_ENABLE_FAILED userId={} error={}", id, e.getMessage());
            }
        }
        entity.setActive(true);
        UserResponse result = toResponse(userRepository.save(entity));
        log.info("action=USER_ACTIVATED userId={} tenantId={}", id, entity.getTenantId());
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserEntity syncToLocal(KeycloakUser kc, UserEntity local, String tenantId) {
        if (local == null) {
            local = new UserEntity();
            local.setEmail(kc.email());
        }
        local.setKeycloakUserId(kc.id());
        local.setTenantId(UUID.fromString(tenantId));
        local.setFirstName(kc.firstName());
        local.setLastName(kc.lastName());
        local.setActive(kc.enabled());
        if (local.getRoles() == null || local.getRoles().isEmpty()) {
            // Populate from Keycloak attributes if not set locally
            List<String> perms = kc.permissions();
            if (!perms.isEmpty()) local.setPermissions(perms);
        }
        try {
            return userRepository.save(local);
        } catch (Exception e) {
            log.debug("action=SYNC_LOCAL_SKIP email={} reason={}", kc.email(), e.getMessage());
            return local;
        }
    }

    private String tenantId() {
        String t = TenantContextHolder.get();
        return t != null ? t : DEFAULT_TENANT;
    }

    private List<String> derivePermissions(List<String> roles) {
        if (roles == null) return List.of();
        boolean admin = roles.contains("administrator");
        if (admin) return List.of("catalog:read", "catalog:write", "catalog:admin");
        return List.of("catalog:read", "catalog:write");
    }

    private UserEntity findOrThrow(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
    }

    UserResponse toResponse(UserEntity e) {
        return new UserResponse(
            e.getId(), e.getTenantId(), e.getEmail(),
            e.getFirstName(), e.getLastName(), e.isActive(),
            e.getRoles(), e.getPermissions(), e.getCreatedAt(),
            e.getKeycloakUserId()
        );
    }
}
