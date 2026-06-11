package com.odin.catalog.policy.infrastructure.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dataset_policy_links",
    uniqueConstraints = @UniqueConstraint(columnNames = {"dataset_id", "tenant_id", "piece_id"}))
@Getter
@Setter
@NoArgsConstructor
public class DatasetPolicyLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "piece_id", nullable = false)
    private PolicyPieceEntity piece;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private OffsetDateTime appliedAt = OffsetDateTime.now();
}
