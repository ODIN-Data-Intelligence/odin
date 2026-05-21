package com.odin.catalog.lineage.infrastructure.jpa.repository;

import com.odin.catalog.lineage.infrastructure.jpa.entity.LineageJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LineageJobRepository extends JpaRepository<LineageJobEntity, UUID> {

    Optional<LineageJobEntity> findByNamespaceAndName(String namespace, String name);
}
