package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.LogicalDataElementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LogicalDataElementRepository extends JpaRepository<LogicalDataElementEntity, UUID> {

    List<LogicalDataElementEntity> findByLogicalModelIdOrderByOrdinalAsc(UUID logicalModelId);

    Optional<LogicalDataElementEntity> findByLogicalModelIdAndNameIgnoreCase(UUID logicalModelId, String name);
}
