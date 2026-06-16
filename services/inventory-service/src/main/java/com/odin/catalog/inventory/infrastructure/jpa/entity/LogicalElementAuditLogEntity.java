package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "logical_element_audit_log")
@Getter @Setter
public class LogicalElementAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID logicalElementId;

    @Column(nullable = false)
    private UUID logicalModelId;

    @Column(nullable = false)
    private UUID datasetId;

    @Column(nullable = false)
    private String eventType;

    private String changedById;
    private String changedByEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payloadBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payloadAfter;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
