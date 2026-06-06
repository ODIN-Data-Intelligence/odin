package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.TermsClassificationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TermsClassificationRuleRepository extends JpaRepository<TermsClassificationRuleEntity, UUID> {

    List<TermsClassificationRuleEntity> findByPolicySetId(UUID policySetId);

    Optional<TermsClassificationRuleEntity> findByPolicySetIdAndClassification(UUID policySetId, String classification);

    void deleteByPolicySetIdAndClassification(UUID policySetId, String classification);
}
