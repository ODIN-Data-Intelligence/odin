package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetSemanticTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatasetSemanticTagRepository extends JpaRepository<DatasetSemanticTagEntity, UUID> {

    List<DatasetSemanticTagEntity> findByDatasetIdOrderByCreatedAtDesc(UUID datasetId);

    Optional<DatasetSemanticTagEntity> findByIdAndDatasetId(UUID id, UUID datasetId);

    void deleteByDatasetId(UUID datasetId);
}
