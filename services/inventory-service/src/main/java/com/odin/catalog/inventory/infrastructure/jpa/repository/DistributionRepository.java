package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.DistributionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DistributionRepository extends JpaRepository<DistributionEntity, UUID> {

    Optional<DistributionEntity> findByIdAndTenantIdAndIsDeletedFalse(UUID id, UUID tenantId);

    List<DistributionEntity> findByDatasetIdAndIsDeletedFalse(UUID datasetId);

    Optional<DistributionEntity> findByDatasetIdAndDownloadUrlAndIsDeletedFalse(UUID datasetId, String downloadUrl);

    Optional<DistributionEntity> findByDatasetIdAndAccessUrlAndIsDeletedFalse(UUID datasetId, String accessUrl);

    Page<DistributionEntity> findByTenantIdAndIsDeletedFalse(UUID tenantId, Pageable pageable);
}
