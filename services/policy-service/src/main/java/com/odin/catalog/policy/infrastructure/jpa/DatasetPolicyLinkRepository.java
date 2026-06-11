package com.odin.catalog.policy.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DatasetPolicyLinkRepository extends JpaRepository<DatasetPolicyLinkEntity, UUID> {

    @Query("SELECT l FROM DatasetPolicyLinkEntity l JOIN FETCH l.piece WHERE l.datasetId = :datasetId AND l.tenantId = :tenantId")
    List<DatasetPolicyLinkEntity> findByDatasetIdAndTenantId(UUID datasetId, UUID tenantId);

    void deleteByDatasetIdAndTenantId(UUID datasetId, UUID tenantId);
}
