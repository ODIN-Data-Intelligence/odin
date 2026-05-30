package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DatasetAuditLogRepository extends JpaRepository<DatasetAuditLogEntity, UUID> {

    Page<DatasetAuditLogEntity> findByDatasetIdOrderByCreatedAtDesc(UUID datasetId, Pageable pageable);

    Page<DatasetAuditLogEntity> findByChangedByIdAndTenantIdOrderByCreatedAtDesc(String changedById, UUID tenantId, Pageable pageable);
}
