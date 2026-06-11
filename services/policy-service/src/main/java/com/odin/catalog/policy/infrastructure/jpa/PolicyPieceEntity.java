package com.odin.catalog.policy.infrastructure.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy_pieces",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "piece_type", "dimension_key"}))
@Getter
@Setter
@NoArgsConstructor
public class PolicyPieceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "piece_type", nullable = false, length = 32)
    private String pieceType;

    @Column(name = "dimension_key", nullable = false, length = 128)
    private String dimensionKey;

    @Column(name = "label")
    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_json", nullable = false, columnDefinition = "jsonb")
    private String policyJson;

    @Column(name = "policy_level", nullable = false, length = 4)
    private String policyLevel = "A";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
