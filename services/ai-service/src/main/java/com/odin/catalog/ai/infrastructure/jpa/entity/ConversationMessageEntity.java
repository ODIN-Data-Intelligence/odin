package com.odin.catalog.ai.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_messages")
@Getter @Setter
public class ConversationMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private String role;    // user, assistant, system, tool

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private Integer tokenCount;
    private String modelUsed;

    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = OffsetDateTime.now(); }
}
