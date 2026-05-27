package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.OwnershipProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnershipProposalRepository extends JpaRepository<OwnershipProposalEntity, UUID> {

    List<OwnershipProposalEntity> findByDatasetIdAndStatus(UUID datasetId, String status);

    Optional<OwnershipProposalEntity> findByIdAndDatasetId(UUID id, UUID datasetId);
}
