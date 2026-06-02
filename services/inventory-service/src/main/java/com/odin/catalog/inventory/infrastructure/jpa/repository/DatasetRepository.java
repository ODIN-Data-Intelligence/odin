package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface DatasetRepository extends JpaRepository<DatasetEntity, UUID> {

    Page<DatasetEntity> findByTenantIdAndIsDeletedFalse(UUID tenantId, Pageable pageable);

    Page<DatasetEntity> findByTenantIdAndCatalogIdAndIsDeletedFalse(UUID tenantId, UUID catalogId, Pageable pageable);

    java.util.List<DatasetEntity> findByCatalogIdAndTenantIdAndIsDeletedFalse(UUID catalogId, UUID tenantId);

    java.util.List<DatasetEntity> findBySourceUri(String sourceUri);

    @Query(value = "SELECT d.* FROM datasets d JOIN resources r ON r.id = d.resource_id WHERE r.tenant_id = :tenantId AND r.is_deleted = false AND :keyword = ANY(r.keywords)",
           countQuery = "SELECT count(*) FROM datasets d JOIN resources r ON r.id = d.resource_id WHERE r.tenant_id = :tenantId AND r.is_deleted = false AND :keyword = ANY(r.keywords)",
           nativeQuery = true)
    Page<DatasetEntity> findByTenantIdAndKeyword(UUID tenantId, String keyword, Pageable pageable);

    long countByOwnerIdAndTenantIdAndIsDeletedFalse(UUID ownerId, UUID tenantId);
}
