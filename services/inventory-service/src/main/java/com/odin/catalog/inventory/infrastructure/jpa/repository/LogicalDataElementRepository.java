package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.LogicalDataElementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LogicalDataElementRepository extends JpaRepository<LogicalDataElementEntity, UUID> {

    List<LogicalDataElementEntity> findByLogicalModelIdOrderByOrdinalAsc(UUID logicalModelId);

    Optional<LogicalDataElementEntity> findByLogicalModelIdAndNameIgnoreCase(UUID logicalModelId, String name);

    @Query(nativeQuery = true, value = """
        SELECT DISTINCT lde.classification
        FROM logical_data_elements lde
        JOIN logical_models lm ON lm.id = lde.logical_model_id
        WHERE lm.dataset_id = :datasetId
          AND lde.classification IS NOT NULL
        """)
    List<String> findClassificationsByDatasetId(@Param("datasetId") UUID datasetId);

    @Query(nativeQuery = true, value = """
        SELECT COUNT(*)
        FROM logical_data_elements lde
        JOIN logical_models lm ON lm.id = lde.logical_model_id
        WHERE lm.dataset_id = :datasetId
          AND lm.status = 'published'
        """)
    long countPublishedByDatasetId(@Param("datasetId") UUID datasetId);

    @Query(nativeQuery = true, value = """
        SELECT COUNT(*)
        FROM logical_data_elements lde
        JOIN logical_models lm ON lm.id = lde.logical_model_id
        WHERE lm.dataset_id = :datasetId
          AND lm.status = 'published'
          AND lde.classification IS NOT NULL
        """)
    long countClassifiedPublishedByDatasetId(@Param("datasetId") UUID datasetId);

    @Query(nativeQuery = true, value = """
        SELECT COUNT(*)
        FROM logical_data_elements lde
        JOIN logical_models lm ON lm.id = lde.logical_model_id
        WHERE lm.dataset_id = :datasetId
          AND lde.is_personal_information = true
        """)
    long countPiiElementsByDatasetId(@Param("datasetId") UUID datasetId);
}
