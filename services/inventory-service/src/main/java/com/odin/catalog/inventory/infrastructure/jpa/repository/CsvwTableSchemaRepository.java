package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.CsvwTableSchemaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CsvwTableSchemaRepository extends JpaRepository<CsvwTableSchemaEntity, UUID> {

    Optional<CsvwTableSchemaEntity> findByTableId(UUID tableId);
}
