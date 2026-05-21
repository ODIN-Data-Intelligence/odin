package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.DatasetVocabularyProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DatasetVocabularyProfileRepository extends JpaRepository<DatasetVocabularyProfileEntity, UUID> {
    List<DatasetVocabularyProfileEntity> findByDatasetId(UUID datasetId);
    boolean existsByDatasetIdAndVocabularyId(UUID datasetId, UUID vocabularyId);
    void deleteByDatasetIdAndVocabularyId(UUID datasetId, UUID vocabularyId);
}
