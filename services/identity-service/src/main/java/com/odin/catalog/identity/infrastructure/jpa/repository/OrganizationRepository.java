package com.odin.catalog.identity.infrastructure.jpa.repository;

import com.odin.catalog.identity.infrastructure.jpa.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
    Optional<OrganizationEntity> findByName(String name);
}
