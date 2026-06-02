package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "distributions")
@DiscriminatorValue("DISTRIBUTION")
@PrimaryKeyJoinColumn(name = "resource_id")
@Getter @Setter
public class DistributionEntity extends ResourceEntity {

    @Column(nullable = false)
    private UUID datasetId;

    private String accessUrl;
    private String downloadUrl;
    private String mediaType;
    private String format;
    private Long byteSize;
    private String checksumAlgorithm;
    private String checksumValue;
    private String compressFormat;
    private String packageFormat;
    private String availability;
    private UUID csvwTableId;

    private String databaseName;
    private String schemaName;
    private String tableName;
}
