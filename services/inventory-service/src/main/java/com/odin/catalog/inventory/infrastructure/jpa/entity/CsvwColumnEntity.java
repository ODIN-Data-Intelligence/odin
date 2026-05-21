package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "csvw_columns")
@Getter @Setter
public class CsvwColumnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID schemaId;

    @Column(nullable = false)
    private Integer ordinal;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> titles;

    private String datatype;
    private Boolean required;
    private Boolean virtual;
    private Boolean suppressOutput;
    private String lang;
    private String defaultValue;
    private String propertyUrl;
    private String valueUrl;
    private String aboutUrl;
    private String description;

    private UUID logicalDataElementId;
}
