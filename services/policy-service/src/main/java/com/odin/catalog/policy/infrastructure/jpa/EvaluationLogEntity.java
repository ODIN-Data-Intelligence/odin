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
@Table(name = "evaluation_log")
@Getter
@Setter
@NoArgsConstructor
public class EvaluationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_context", columnDefinition = "jsonb")
    private String requestContext;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
