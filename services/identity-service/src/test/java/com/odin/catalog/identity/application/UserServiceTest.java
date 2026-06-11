package com.odin.catalog.identity.application;

import com.odin.catalog.identity.api.v1.dto.UserRequest;
import com.odin.catalog.identity.api.v1.dto.UserResponse;
import com.odin.catalog.identity.infrastructure.jpa.entity.UserEntity;
import com.odin.catalog.identity.infrastructure.jpa.repository.UserRepository;
import com.odin.catalog.identity.keycloak.KeycloakAdminClient;
import com.odin.catalog.identity.keycloak.KeycloakAdminClient.KeycloakUser;
import com.odin.catalog.identity.keycloak.KeycloakAdminClient.RealmRole;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock UserRepository userRepository;
    @Mock KeycloakAdminClient keycloak;

    @InjectMocks UserService service;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.set(TENANT.toString());
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.set(null);
    }

    // ── list ─────────────────────────────────────────────────────────────

    @Test
    void list_keycloakFails_fallsBackToLocalDb() {
        UserEntity u = user("alice@example.com");
        var pageable = PageRequest.of(0, 20);
        when(keycloak.listUsers()).thenThrow(new RuntimeException("KC unavailable"));
        when(userRepository.findByTenantId(eq(TENANT), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(u)));

        List<UserResponse> result = service.list(pageable);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("alice@example.com");
        assertThat(result.get(0).tenantId()).isEqualTo(TENANT);
    }

    @Test
    void list_keycloakReturnsUsers_mergesWithLocalDb() {
        var pageable = PageRequest.of(0, 20);
        String kcId = UUID.randomUUID().toString();
        KeycloakUser kcUser = new KeycloakUser(kcId, "bobsmith", "bob@example.com", "Bob", "Smith", true, java.util.Map.of());
        when(keycloak.listUsers()).thenReturn(List.of(kcUser));
        when(keycloak.getUserRealmRoles(kcId)).thenReturn(List.of(new RealmRole("role-id-1", "data-steward")));

        UserEntity local = user("bob@example.com");
        local.setKeycloakUserId(kcId);
        when(userRepository.findAll()).thenReturn(List.of(local));
        when(userRepository.save(any())).thenReturn(local);

        List<UserResponse> result = service.list(pageable);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("bob@example.com");
    }

    @Test
    void list_keycloakFails_emptyFallback_returnsEmptyList() {
        var pageable = PageRequest.of(0, 20);
        when(keycloak.listUsers()).thenThrow(new RuntimeException("KC unavailable"));
        when(userRepository.findByTenantId(any(), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.list(pageable)).isEmpty();
    }

    // ── get ──────────────────────────────────────────────────────────────

    @Test
    void get_found_returnsResponse() {
        UserEntity u = user("bob@example.com");
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

        UserResponse result = service.get(u.getId());

        assertThat(result.id()).isEqualTo(u.getId());
        assertThat(result.email()).isEqualTo("bob@example.com");
        assertThat(result.active()).isTrue();
    }

    @Test
    void get_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(id.toString());
    }

    // ── invite ────────────────────────────────────────────────────────────

    @Test
    void invite_existingEmail_updatesExistingEntity() {
        UserEntity existing = user("carol@example.com");
        when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenReturn(existing);

        UserRequest req = new UserRequest("carol@example.com", "Carol", "Updated", List.of("data-steward"));
        service.invite(req);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getFirstName()).isEqualTo("Carol");
    }

    @Test
    void invite_persistsUserWithTenantAndReturnsResponse() {
        UserRequest req = new UserRequest("carol@example.com", "Carol", "Smith", List.of("data-steward"));
        UserEntity saved = user("carol@example.com");
        saved.setFirstName("Carol");
        saved.setLastName("Smith");
        saved.setRoles(List.of("data-steward"));
        when(userRepository.save(any())).thenReturn(saved);

        UserResponse result = service.invite(req);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity captured = captor.getValue();
        assertThat(captured.getTenantId()).isEqualTo(TENANT);
        assertThat(captured.getEmail()).isEqualTo("carol@example.com");
        assertThat(captured.getFirstName()).isEqualTo("Carol");
        assertThat(captured.getRoles()).containsExactly("data-steward");
        assertThat(result.email()).isEqualTo("carol@example.com");
    }

    @Test
    void invite_nullRoles_persistsWithNullRoles() {
        UserRequest req = new UserRequest("dave@example.com", null, null, null);
        UserEntity saved = user("dave@example.com");
        when(userRepository.save(any())).thenReturn(saved);

        service.invite(req);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles()).isNull();
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_found_appliesChanges() {
        UserEntity u = user("eve@example.com");
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        UserRequest req = new UserRequest("eve@example.com", "Eva", "Brown", List.of("data-owner"));
        UserResponse result = service.update(u.getId(), req);

        assertThat(u.getFirstName()).isEqualTo("Eva");
        assertThat(u.getLastName()).isEqualTo("Brown");
        assertThat(u.getRoles()).containsExactly("data-owner");
        assertThat(result.id()).isEqualTo(u.getId());
    }

    @Test
    void update_withKeycloakId_callsKeycloakUpdate() {
        UserEntity u = user("frank@example.com");
        String kcId = UUID.randomUUID().toString();
        u.setKeycloakUserId(kcId);
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        service.update(u.getId(), new UserRequest("frank@example.com", "Frank", null, null));

        verify(keycloak).updateUser(eq(kcId), eq("Frank"), any(), any(), any(), any());
    }

    @Test
    void update_nullRoles_skipsPermissionsUpdate() {
        UserEntity u = user("helen@example.com");
        u.setRoles(List.of("data-owner"));
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        service.update(u.getId(), new UserRequest("helen@example.com", null, null, null));

        // roles remain unchanged since request.roles() was null
        assertThat(u.getRoles()).containsExactly("data-owner");
    }

    @Test
    void update_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UserRequest("x@x.com", null, null, null)))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── activate ──────────────────────────────────────────────────────────

    @Test
    void activate_found_setsActiveToTrue() {
        UserEntity u = user("grace@example.com");
        u.setActive(false);
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        UserResponse result = service.activate(u.getId());

        assertThat(u.isActive()).isTrue();
        assertThat(result.active()).isTrue();
    }

    @Test
    void activate_withKeycloakId_callsSetEnabled() {
        UserEntity u = user("george@example.com");
        u.setActive(false);
        String kcId = UUID.randomUUID().toString();
        u.setKeycloakUserId(kcId);
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        service.activate(u.getId());

        verify(keycloak).setEnabled(kcId, true);
    }

    @Test
    void activate_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(id))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── getByKeycloakId ───────────────────────────────────────────────────

    @Test
    void getByKeycloakId_localEntityExists_returnsFast() {
        String kcId = UUID.randomUUID().toString();
        UserEntity local = user("henry@example.com");
        when(userRepository.findByKeycloakUserId(kcId)).thenReturn(Optional.of(local));

        UserResponse result = service.getByKeycloakId(kcId);

        assertThat(result.email()).isEqualTo("henry@example.com");
        verify(keycloak, never()).getUserById(any());
    }

    @Test
    void getByKeycloakId_notFoundAnywhere_throwsNoSuchElement() {
        String kcId = UUID.randomUUID().toString();
        when(userRepository.findByKeycloakUserId(kcId)).thenReturn(Optional.empty());
        when(keycloak.getUserById(kcId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByKeycloakId(kcId))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getByKeycloakId_keycloakFallback_createsLocalEntity() {
        String kcId = UUID.randomUUID().toString();
        KeycloakUser kcUser = new KeycloakUser(kcId, "ivan", "ivan@example.com", "Ivan", "Ivanov", true, Map.of());
        when(userRepository.findByKeycloakUserId(kcId)).thenReturn(Optional.empty());
        when(keycloak.getUserById(kcId)).thenReturn(Optional.of(kcUser));
        when(userRepository.findByEmail("ivan@example.com")).thenReturn(Optional.empty());

        UserEntity saved = user("ivan@example.com");
        saved.setKeycloakUserId(kcId);
        when(userRepository.save(any())).thenReturn(saved);

        UserResponse result = service.getByKeycloakId(kcId);

        assertThat(result.email()).isEqualTo("ivan@example.com");
    }

    @Test
    void getByKeycloakId_keycloakFallback_nullEmail_throwsNoSuchElement() {
        String kcId = UUID.randomUUID().toString();
        KeycloakUser kcUser = new KeycloakUser(kcId, "nomail", null, "No", "Mail", true, Map.of());
        when(userRepository.findByKeycloakUserId(kcId)).thenReturn(Optional.empty());
        when(keycloak.getUserById(kcId)).thenReturn(Optional.of(kcUser));

        assertThatThrownBy(() -> service.getByKeycloakId(kcId))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── invite (administrator role) ───────────────────────────────────────

    @Test
    void invite_administratorRole_getsAdminPermissions() {
        UserRequest req = new UserRequest("admin@example.com", "Admin", "User", List.of("administrator"));
        UserEntity saved = user("admin@example.com");
        saved.setRoles(List.of("administrator"));
        saved.setPermissions(List.of("catalog:read", "catalog:write", "catalog:admin"));
        when(userRepository.save(any())).thenReturn(saved);

        UserResponse result = service.invite(req);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPermissions()).contains("catalog:admin");
        assertThat(result.email()).isEqualTo("admin@example.com");
    }

    @Test
    void list_kcUserNullEmail_isFiltered() {
        var pageable = PageRequest.of(0, 20);
        KeycloakUser noEmail = new KeycloakUser(UUID.randomUUID().toString(), "nomail", null, "No", "Email", true, Map.of());
        when(keycloak.listUsers()).thenReturn(List.of(noEmail));
        when(userRepository.findAll()).thenReturn(List.of());

        List<UserResponse> result = service.list(pageable);

        assertThat(result).isEmpty();
    }

    @Test
    void list_localUserHasRoles_usesLocalRolesAndSkipsKcRoles() {
        var pageable = PageRequest.of(0, 20);
        String kcId = UUID.randomUUID().toString();
        KeycloakUser kcUser = new KeycloakUser(kcId, "alice", "alice@example.com", "Alice", "Liddell", true, Map.of());
        when(keycloak.listUsers()).thenReturn(List.of(kcUser));

        UserEntity local = user("alice@example.com");
        local.setKeycloakUserId(kcId);
        local.setRoles(List.of("data-steward"));
        when(userRepository.findAll()).thenReturn(List.of(local));
        when(userRepository.save(any())).thenReturn(local);

        List<UserResponse> result = service.list(pageable);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roles()).containsExactly("data-steward");
        verify(keycloak, never()).getUserRealmRoles(any());
    }

    @Test
    void list_syncToLocal_withPermissions_setsPermissionsFromKc() {
        var pageable = PageRequest.of(0, 20);
        String kcId = UUID.randomUUID().toString();
        KeycloakUser kcUser = new KeycloakUser(kcId, "bob", "bob@example.com", "Bob", "Smith", true,
            Map.of("permissions", List.of("catalog:read", "catalog:write")));
        when(keycloak.listUsers()).thenReturn(List.of(kcUser));
        when(keycloak.getUserRealmRoles(kcId)).thenReturn(List.of());

        UserEntity local = user("bob@example.com");
        local.setKeycloakUserId(kcId);
        // No roles set → syncToLocal will enter the permissions block
        when(userRepository.findAll()).thenReturn(List.of(local));
        when(userRepository.save(any())).thenReturn(local);

        service.list(pageable);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPermissions()).containsExactly("catalog:read", "catalog:write");
    }

    @Test
    void list_syncToLocal_nullLocal_createsNewEntity() {
        var pageable = PageRequest.of(0, 20);
        String kcId = UUID.randomUUID().toString();
        KeycloakUser kcUser = new KeycloakUser(kcId, "newuser", "new@example.com", "New", "User", true, Map.of());
        when(keycloak.listUsers()).thenReturn(List.of(kcUser));
        when(keycloak.getUserRealmRoles(kcId)).thenReturn(List.of());

        // No local entity → byKeycloakId and byEmail both empty → local = null
        when(userRepository.findAll()).thenReturn(List.of());
        UserEntity created = new UserEntity();
        created.setId(UUID.randomUUID());
        created.setEmail("new@example.com");
        created.setTenantId(TENANT);
        when(userRepository.save(any())).thenReturn(created);

        List<UserResponse> result = service.list(pageable);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("new@example.com");
    }

    @Test
    void list_syncToLocal_saveThrows_returnsUnsavedLocal() {
        var pageable = PageRequest.of(0, 20);
        String kcId = UUID.randomUUID().toString();
        KeycloakUser kcUser = new KeycloakUser(kcId, "crash", "crash@example.com", "Crash", "User", true, Map.of());
        when(keycloak.listUsers()).thenReturn(List.of(kcUser));
        when(keycloak.getUserRealmRoles(kcId)).thenReturn(List.of());

        UserEntity local = user("crash@example.com");
        local.setKeycloakUserId(kcId);
        when(userRepository.findAll()).thenReturn(List.of(local));
        when(userRepository.save(any())).thenThrow(new RuntimeException("DB constraint"));

        // Should not throw — catch block returns unsaved entity
        assertThat(service.list(pageable)).hasSize(1);
    }

    // ── deactivate ────────────────────────────────────────────────────────

    @Test
    void deactivate_found_setsActiveToFalse() {
        UserEntity u = user("eve@example.com");
        assertThat(u.isActive()).isTrue();
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        service.deactivate(u.getId());

        assertThat(u.isActive()).isFalse();
        verify(userRepository).save(u);
    }

    @Test
    void deactivate_withKeycloakId_callsSetEnabled() {
        UserEntity u = user("frank2@example.com");
        String kcId = UUID.randomUUID().toString();
        u.setKeycloakUserId(kcId);
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        service.deactivate(u.getId());

        verify(keycloak).setEnabled(kcId, false);
    }

    @Test
    void deactivate_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(id))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(id.toString());
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private UserEntity user(String email) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setTenantId(TENANT);
        u.setEmail(email);
        u.setActive(true);
        return u;
    }
}
