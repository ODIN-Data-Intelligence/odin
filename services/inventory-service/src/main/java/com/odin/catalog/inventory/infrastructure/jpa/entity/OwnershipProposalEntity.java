package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ownership_proposals")
@Getter @Setter
public class OwnershipProposalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID datasetId;

    @Column(nullable = false)
    private UUID proposedOwnerId;

    @Column(nullable = false)
    private UUID proposedById;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime resolvedAt;
}
