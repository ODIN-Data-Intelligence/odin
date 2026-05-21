package com.odin.catalog.shared.models.dcat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.odin.catalog.shared.models.common.ContactPoint;

import java.util.List;
import java.util.Map;

/**
 * Base fields common to all DCAT resources (Catalog, Dataset, Distribution, DataService).
 * Maps to the polymorphic {@code resources} table in inventory-service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DcatResource(
    String id,
    String resourceType,
    String iri,
    String tenantId,
    String domainId,
    String title,
    String description,
    List<String> language,
    List<String> keywords,
    List<String> themes,
    String issued,              // ISO-8601
    String modified,            // ISO-8601
    String license,             // IRI
    String rightsStatement,
    String accessRights,        // IRI
    List<String> conformsTo,    // standard IRIs
    String creatorId,
    String publisherId,
    List<ContactPoint> contactPoints,
    String sourceUri,           // original URI if harvested
    Map<String, Object> extra   // non-modeled properties
) {}
