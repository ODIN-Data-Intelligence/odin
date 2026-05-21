package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "logical_element_vocab_mappings")
@Getter @Setter
public class VocabMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID logicalElementId;

    @Column(nullable = false)
    private UUID vocabularyId;

    @Column(nullable = false)
    private String conceptIri;

    private String conceptLabel;
    private String conceptDefinition;

    @Column(nullable = false)
    private String matchType = "exactMatch";

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
