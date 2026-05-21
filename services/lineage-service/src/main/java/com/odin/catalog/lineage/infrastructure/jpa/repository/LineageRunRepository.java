package com.odin.catalog.lineage.infrastructure.jpa.repository;

import com.odin.catalog.lineage.infrastructure.jpa.entity.LineageRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LineageRunRepository extends JpaRepository<LineageRunEntity, UUID> {

    Optional<LineageRunEntity> findByRunId(String runId);
}
