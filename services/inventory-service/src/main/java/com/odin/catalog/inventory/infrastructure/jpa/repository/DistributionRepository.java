package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.DistributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DistributionRepository extends JpaRepository<DistributionEntity, UUID> {

    List<DistributionEntity> findByDatasetIdAndIsDeletedFalse(UUID datasetId);
}
