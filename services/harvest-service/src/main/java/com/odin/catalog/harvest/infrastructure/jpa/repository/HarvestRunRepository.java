package com.odin.catalog.harvest.infrastructure.jpa.repository;

import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HarvestRunRepository extends JpaRepository<HarvestRunEntity, UUID> {

    List<HarvestRunEntity> findByJobIdOrderByCreatedAtDesc(UUID jobId);

    List<HarvestRunEntity> findByStatusOrderByCreatedAtDesc(String status);

    boolean existsByJobIdAndStatusIn(UUID jobId, Collection<String> statuses);
}
