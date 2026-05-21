package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.DataProductPortEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DataProductPortRepository extends JpaRepository<DataProductPortEntity, UUID> {
    List<DataProductPortEntity> findByDataProductId(UUID dataProductId);
    void deleteByDataProductIdAndDatasetId(UUID dataProductId, UUID datasetId);
}
