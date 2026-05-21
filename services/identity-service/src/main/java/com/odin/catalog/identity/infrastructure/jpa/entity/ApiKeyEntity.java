package com.odin.catalog.identity.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
@Getter @Setter
public class ApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false, unique = true)
    private String keyHash;        // SHA-256 of the raw key

    private String description;
    private boolean active = true;
    private OffsetDateTime expiresAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> scopes;

    private OffsetDateTime createdAt;
    private OffsetDateTime lastUsedAt;

    @PrePersist
    void prePersist() { createdAt = OffsetDateTime.now(); }
}
