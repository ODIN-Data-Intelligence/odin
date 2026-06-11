package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "logical_data_elements")
@Getter @Setter
public class LogicalDataElementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID logicalModelId;

    @Column(nullable = false)
    private String name;

    private String label;
    private String description;
    private String logicalType;

    @Column(nullable = false)
    private boolean isRequired = false;

    @Column(nullable = false)
    private boolean isIdentifier = false;

    @Column(nullable = false)
    private boolean isNullable = true;

    @Column(nullable = false)
    private Integer ordinal;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "logicalElementId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VocabMappingEntity> vocabMappings;

    private String classification;
    private String recommendedClassification;
    private String classificationReasoning;
    private OffsetDateTime classificationRecommendedAt;

    private String recommendedDescription;
    private String descriptionReasoning;
    private OffsetDateTime descriptionRecommendedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_vocab_mappings", columnDefinition = "jsonb")
    private String recommendedVocabMappings;
    private String vocabMappingReasoning;
    private OffsetDateTime vocabMappingRecommendedAt;

    @Column(nullable = false)
    private boolean isPersonalInformation = false;

    @Column(nullable = false)
    private boolean isDirectIdentifier = false;

    private Boolean recommendedIsPersonalInformation;
    private Boolean recommendedIsDirectIdentifier;
    private String piiRecommendationReasoning;
    private OffsetDateTime piiRecommendedAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }
}
