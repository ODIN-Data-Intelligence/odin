package com.odin.catalog.policy.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PolicyRecordRepository extends JpaRepository<PolicyRecordEntity, UUID> {
    Optional<PolicyRecordEntity> findByDatasetIdAndTenantId(UUID datasetId, UUID tenantId);
    void deleteByDatasetIdAndTenantId(UUID datasetId, UUID tenantId);
}
