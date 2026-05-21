package com.odin.catalog.harvest.infrastructure.jpa.repository;

import com.odin.catalog.harvest.infrastructure.jpa.entity.HarvestSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HarvestSourceRepository extends JpaRepository<HarvestSourceEntity, UUID> {

    List<HarvestSourceEntity> findByTenantId(UUID tenantId);

    List<HarvestSourceEntity> findByTenantIdAndSourceType(UUID tenantId, String sourceType);
}
