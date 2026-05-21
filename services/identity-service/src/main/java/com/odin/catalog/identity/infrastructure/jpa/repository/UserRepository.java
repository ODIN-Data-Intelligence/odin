package com.odin.catalog.identity.infrastructure.jpa.repository;

import com.odin.catalog.identity.infrastructure.jpa.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByKeycloakUserId(String keycloakUserId);
    Page<UserEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
