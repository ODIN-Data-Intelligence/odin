package com.odin.catalog.ai.infrastructure.jpa.repository;

import com.odin.catalog.ai.infrastructure.jpa.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
    List<ConversationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
