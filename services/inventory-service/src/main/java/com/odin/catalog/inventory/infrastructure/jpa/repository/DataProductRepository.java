package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.DataProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DataProductRepository extends JpaRepository<DataProductEntity, UUID> {

    Page<DataProductEntity> findByTenantIdAndIsDeletedFalse(UUID tenantId, Pageable pageable);

    Page<DataProductEntity> findByTenantIdAndDomainIdAndIsDeletedFalse(UUID tenantId, UUID domainId, Pageable pageable);

    Page<DataProductEntity> findByTenantIdAndLifecycleStatusAndIsDeletedFalse(UUID tenantId, String lifecycleStatus, Pageable pageable);

    long countByOwnerIdAndTenantIdAndIsDeletedFalse(UUID ownerId, UUID tenantId);
}
