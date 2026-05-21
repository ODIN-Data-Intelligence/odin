package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.VocabularyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VocabularyRepository extends JpaRepository<VocabularyEntity, UUID> {

    List<VocabularyEntity> findByVocabularyType(String vocabularyType);

    Optional<VocabularyEntity> findByPrefix(String prefix);

    Optional<VocabularyEntity> findByBaseIri(String baseIri);
}
