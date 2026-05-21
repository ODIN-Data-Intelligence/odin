package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.VocabMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VocabMappingRepository extends JpaRepository<VocabMappingEntity, UUID> {

    List<VocabMappingEntity> findByLogicalElementId(UUID logicalElementId);

    void deleteByLogicalElementIdAndConceptIri(UUID logicalElementId, String conceptIri);
}
