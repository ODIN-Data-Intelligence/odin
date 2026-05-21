package com.odin.catalog.identity.infrastructure.jpa.repository;

import com.odin.catalog.identity.infrastructure.jpa.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {
    Optional<ApiKeyEntity> findByKeyHashAndActiveTrue(String keyHash);
    java.util.List<ApiKeyEntity> findByTenantIdAndActiveTrue(UUID tenantId);
}
