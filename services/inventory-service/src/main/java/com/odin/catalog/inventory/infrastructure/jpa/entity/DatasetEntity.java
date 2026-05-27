package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "datasets")
@DiscriminatorValue("DATASET")
@PrimaryKeyJoinColumn(name = "resource_id")
@Getter @Setter
public class DatasetEntity extends ResourceEntity {

    private UUID catalogId;
    private String accrualPeriodicity;
    private OffsetDateTime temporalStart;
    private OffsetDateTime temporalEnd;
    @Column(name = "spatial_resolution_m")
    private Double spatialResolutionM;
    private String temporalResolution;
    private String version;
    private String versionNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "is_version_of")
    private DatasetEntity isVersionOf;

    private UUID ownerId;
}
