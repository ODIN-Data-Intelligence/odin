package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.CatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CatalogRepository extends JpaRepository<CatalogEntity, UUID> {
    List<CatalogEntity> findByTenantIdAndIsDeletedFalse(UUID tenantId);
}
