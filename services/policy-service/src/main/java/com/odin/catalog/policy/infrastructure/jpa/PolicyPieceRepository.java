package com.odin.catalog.policy.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PolicyPieceRepository extends JpaRepository<PolicyPieceEntity, UUID> {
    Optional<PolicyPieceEntity> findByTenantIdAndPieceTypeAndDimensionKey(
        UUID tenantId, String pieceType, String dimensionKey);
}
