package com.odin.catalog.lineage.infrastructure.jpa.repository;

import com.odin.catalog.lineage.infrastructure.jpa.entity.LineageDatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LineageDatasetRepository extends JpaRepository<LineageDatasetEntity, UUID> {

    Optional<LineageDatasetEntity> findByNamespaceAndName(String namespace, String name);
    Optional<LineageDatasetEntity> findByCatalogResourceId(UUID catalogResourceId);
}
