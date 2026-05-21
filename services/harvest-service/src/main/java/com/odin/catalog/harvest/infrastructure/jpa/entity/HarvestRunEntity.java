package com.odin.catalog.harvest.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "harvest_runs")
@Getter @Setter
public class HarvestRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private UUID sourceId;

    @Column(nullable = false)
    private String status = "pending";

    private String triggeredBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private Integer entitiesDiscovered;
    private Integer entitiesCreated;
    private Integer entitiesUpdated;
    private Integer entitiesFailed;
    private String snapshotPath;
    private String errorMessage;
    private boolean fullRefresh;

    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
