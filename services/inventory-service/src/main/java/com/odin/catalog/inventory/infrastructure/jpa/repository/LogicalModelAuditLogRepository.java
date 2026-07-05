package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.LogicalModelAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LogicalModelAuditLogRepository extends JpaRepository<LogicalModelAuditLogEntity, UUID> {

    Page<LogicalModelAuditLogEntity> findByDatasetIdOrderByCreatedAtDesc(UUID datasetId, Pageable pageable);
}
