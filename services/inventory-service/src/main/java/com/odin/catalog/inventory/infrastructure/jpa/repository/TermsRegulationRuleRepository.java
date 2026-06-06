package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.TermsRegulationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TermsRegulationRuleRepository extends JpaRepository<TermsRegulationRuleEntity, UUID> {

    List<TermsRegulationRuleEntity> findByPolicySetId(UUID policySetId);
}
