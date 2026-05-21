package com.odin.catalog.lineage.infrastructure.jpa.repository;

import com.odin.catalog.lineage.infrastructure.jpa.entity.LineageRunEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LineageRunEventRepository extends JpaRepository<LineageRunEventEntity, UUID> {

    List<LineageRunEventEntity> findByRunIdOrderByEventTimeAsc(UUID runId);
}
