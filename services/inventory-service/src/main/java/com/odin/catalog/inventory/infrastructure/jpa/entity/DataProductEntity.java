package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "data_products")
@DiscriminatorValue("DATA_PRODUCT")
@PrimaryKeyJoinColumn(name = "resource_id")
@Getter @Setter
public class DataProductEntity extends ResourceEntity {

    @Column(nullable = false)
    private String lifecycleStatus = "Ideation";

    private UUID ownerId;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    private String informationSensitivity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String hasPolicy;
}
