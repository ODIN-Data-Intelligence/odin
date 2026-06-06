package com.odin.catalog.inventory.infrastructure.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "terms_regulation_rules")
@Getter @Setter
public class TermsRegulationRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID policySetId;

    @Column(nullable = false)
    private String signalType;

    @Column(nullable = false)
    private String pattern;

    @Column(nullable = false)
    private String regulationName;

    @Column(nullable = false)
    private String signalLabel;
}
