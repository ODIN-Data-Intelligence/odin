package com.odin.catalog.inventory.infrastructure.jpa.repository;

import com.odin.catalog.inventory.infrastructure.jpa.entity.TermsPolicySetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TermsPolicySetRepository extends JpaRepository<TermsPolicySetEntity, UUID> {

    Optional<TermsPolicySetEntity> findFirstByStatus(String status);

    List<TermsPolicySetEntity> findAllByOrderByCreatedAtDesc();
}
