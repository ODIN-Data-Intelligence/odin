package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dataset_semantic_tags")
@Getter @Setter
public class DatasetSemanticTagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID datasetId;

    @Column(nullable = false)
    private String semanticType;

    private String vocabularyIri;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
