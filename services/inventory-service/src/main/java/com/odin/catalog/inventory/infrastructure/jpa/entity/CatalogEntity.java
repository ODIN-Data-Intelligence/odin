package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "catalogs")
@DiscriminatorValue("CATALOG")
@PrimaryKeyJoinColumn(name = "resource_id")
@Getter @Setter
public class CatalogEntity extends ResourceEntity {

    private String homepage;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "has_part", columnDefinition = "uuid[]")
    private List<java.util.UUID> hasPart;
}
