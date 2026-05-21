package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.LogicalModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LogicalModelRepository extends JpaRepository<LogicalModelEntity, UUID> {

    List<LogicalModelEntity> findByDatasetIdOrderByCreatedAtDesc(UUID datasetId);

    Optional<LogicalModelEntity> findByDatasetIdAndStatus(UUID datasetId, String status);
}
