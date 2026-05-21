package com.odin.catalog.ai.infrastructure.jpa.repository;

import com.odin.catalog.ai.infrastructure.jpa.entity.ConversationMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessageEntity, UUID> {
    List<ConversationMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
