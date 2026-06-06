package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.TermsRegulationObligationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TermsRegulationObligationRepository extends JpaRepository<TermsRegulationObligationEntity, UUID> {

    List<TermsRegulationObligationEntity> findByPolicySetId(UUID policySetId);
}
