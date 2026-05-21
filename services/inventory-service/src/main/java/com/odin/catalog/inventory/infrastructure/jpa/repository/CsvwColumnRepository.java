package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.CsvwColumnEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CsvwColumnRepository extends JpaRepository<CsvwColumnEntity, UUID> {

    List<CsvwColumnEntity> findBySchemaIdOrderByOrdinalAsc(UUID schemaId);

    Optional<CsvwColumnEntity> findBySchemaIdAndNameIgnoreCase(UUID schemaId, String name);

    List<CsvwColumnEntity> findByLogicalDataElementId(UUID logicalDataElementId);

    @Modifying
    @Query("UPDATE CsvwColumnEntity c SET c.logicalDataElementId = :elementId WHERE c.id = :columnId")
    int bindLogicalElement(UUID columnId, UUID elementId);

    @Modifying
    @Query("UPDATE CsvwColumnEntity c SET c.logicalDataElementId = NULL WHERE c.logicalDataElementId = :elementId")
    int unbindLogicalElement(UUID elementId);

    @Modifying
    @Query("DELETE FROM CsvwColumnEntity c WHERE c.schemaId = :schemaId")
    void deleteBySchemaId(UUID schemaId);
}
