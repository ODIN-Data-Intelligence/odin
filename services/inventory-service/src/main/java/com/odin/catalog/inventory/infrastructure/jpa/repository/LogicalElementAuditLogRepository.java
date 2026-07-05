package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.LogicalElementAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LogicalElementAuditLogRepository extends JpaRepository<LogicalElementAuditLogEntity, UUID> {

    Page<LogicalElementAuditLogEntity> findByDatasetIdOrderByCreatedAtDesc(UUID datasetId, Pageable pageable);
}
