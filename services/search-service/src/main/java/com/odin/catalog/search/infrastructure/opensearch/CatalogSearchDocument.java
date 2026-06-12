package com.odin.catalog.search.infrastructure.opensearch;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogSearchDocument(
    String id,
    String tenantId,
    String entityType,
    String title,
    String description,
    List<String> keywords,
    List<String> themes,
    String domainId,
    String domainName,
    String ownerId,
    String ownerName,
    String lifecycleStatus,
    String informationSensitivity,
    String license,
    String format,
    String mediaType,
    String accrualPeriodicity,
    String sourceUri,
    String issued,
    String modified,
    boolean isDeleted,
    boolean hasLineage,
    boolean hasLogicalModel,
    int distributionCount,
    List<String> distributionFormats,
    // Logical model fields
    List<String> logicalElementNames,
    List<String> logicalElementLabels,
    List<String> logicalTypes,
    List<String> vocabConceptIris,
    List<String> vocabConceptLabels,
    List<String> vocabularyTypes,
    List<String> fiboConcepts,
    List<String> semanticTypes,
    // Physical schema fields
    List<String> columnNames,
    List<String> columnDescriptions,
    // Parent reference — set for DISTRIBUTION documents only
    String datasetId
) {}
