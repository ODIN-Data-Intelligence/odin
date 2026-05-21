package com.odin.catalog.identity.application;

import com.odin.catalog.identity.api.v1.dto.UserRequest;
import com.odin.catalog.identity.api.v1.dto.UserResponse;
import com.odin.catalog.identity.infrastructure.jpa.entity.UserEntity;
import com.odin.catalog.identity.infrastructure.jpa.repository.UserRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
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

    @InjectMocks UserService service;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.set(TENANT.toString());
    }

    // ── list ─────────────────────────────────────────────────────────────

    @Test
    void list_returnsPageMappedToResponses() {
        UserEntity u = user("alice@example.com");
        var pageable = PageRequest.of(0, 20);
        when(userRepository.findByTenantId(eq(TENANT), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(u)));

        List<UserResponse> result = service.list(pageable);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("alice@example.com");
        assertThat(result.get(0).tenantId()).isEqualTo(TENANT);
    }

    @Test
    void list_emptyRepository_returnsEmptyList() {
        var pageable = PageRequest.of(0, 20);
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
