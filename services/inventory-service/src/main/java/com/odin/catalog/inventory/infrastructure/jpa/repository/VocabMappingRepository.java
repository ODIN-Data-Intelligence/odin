package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.VocabMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VocabMappingRepository extends JpaRepository<VocabMappingEntity, UUID> {

    List<VocabMappingEntity> findByLogicalElementId(UUID logicalElementId);

    void deleteByLogicalElementIdAndConceptIri(UUID logicalElementId, String conceptIri);

    /**
     * Returns distinct semantic type labels derived from vocab concept IRIs.
     * Only considers exactMatch/closeMatch mappings on elements belonging to published logical models.
     * The label is the terminal path segment after the last '/' or '#'.
     */
    @Query(nativeQuery = true, value = """
        SELECT DISTINCT
          CASE
            WHEN levm.concept_iri LIKE '%#%'
              THEN split_part(levm.concept_iri, '#', -1)
            ELSE
              split_part(levm.concept_iri, '/', -1)
          END
        FROM logical_element_vocab_mappings levm
        JOIN logical_data_elements lde ON lde.id = levm.logical_element_id
        JOIN logical_models lm ON lm.id = lde.logical_model_id
        WHERE lm.dataset_id = :datasetId
          AND lm.status = 'published'
          AND levm.match_type IN ('exactMatch', 'closeMatch')
          AND levm.concept_iri IS NOT NULL
          AND split_part(levm.concept_iri, '/', -1) <> ''
        """)
    List<String> findSemanticTypesByDatasetId(@Param("datasetId") UUID datasetId);
}
