package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.BulkRecommendationJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BulkRecommendationJobRepository extends JpaRepository<BulkRecommendationJobEntity, UUID> {
}
