package com.odin.catalog.harvest.infrastructure.jpa.repository;

import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HarvestJobRepository extends JpaRepository<HarvestJobEntity, UUID> {

    List<HarvestJobEntity> findBySourceIdAndEnabledTrue(UUID sourceId);
    List<HarvestJobEntity> findBySourceId(UUID sourceId);
}
