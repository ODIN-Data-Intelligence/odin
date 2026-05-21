package com.odin.catalog.identity.application;

import com.odin.catalog.identity.api.v1.dto.UserRequest;
import com.odin.catalog.identity.api.v1.dto.UserResponse;
import com.odin.catalog.identity.infrastructure.jpa.entity.UserEntity;
import com.odin.catalog.identity.infrastructure.jpa.repository.UserRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> list(Pageable pageable) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        return userRepository.findByTenantId(tenantId, pageable)
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public UserResponse invite(UserRequest request) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        UserEntity entity = new UserEntity();
        entity.setTenantId(tenantId);
        entity.setEmail(request.email());
        entity.setFirstName(request.firstName());
        entity.setLastName(request.lastName());
        entity.setRoles(request.roles());
        return toResponse(userRepository.save(entity));
    }

    @Transactional
    public void deactivate(UUID id) {
        UserEntity entity = findOrThrow(id);
        entity.setActive(false);
        userRepository.save(entity);
    }

    private UserEntity findOrThrow(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
    }

    UserResponse toResponse(UserEntity e) {
        return new UserResponse(
            e.getId(), e.getTenantId(), e.getEmail(),
            e.getFirstName(), e.getLastName(), e.isActive(),
            e.getRoles(), e.getPermissions(), e.getCreatedAt()
        );
    }
}
