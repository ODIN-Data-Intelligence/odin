package com.odin.catalog.policy.infrastructure.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EvaluationLogRepository extends JpaRepository<EvaluationLogEntity, UUID> {
    Page<EvaluationLogEntity> findByDatasetIdAndTenantIdOrderByCreatedAtDesc(
        UUID datasetId, UUID tenantId, Pageable pageable);
}
