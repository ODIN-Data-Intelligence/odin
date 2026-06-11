package com.odin.catalog.inventory.api.v1.dto;

import com.odin.catalog.inventory.infrastructure.jpa.entity.CatalogEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "DCAT Catalog")
public record CatalogResponse(

    UUID id,
    String iri,
    UUID tenantId,
    UUID domainId,
    String title,
    String description,
    String homepage,
    List<String> keywords,
    List<String> themes,
    List<String> language,
    String license,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt

) {
    public static CatalogResponse from(CatalogEntity e) {
        return new CatalogResponse(
            e.getId(), e.getIri(), e.getTenantId(), e.getDomainId(),
            e.getTitle(), e.getDescription(), e.getHomepage(),
            e.getKeywords(), e.getThemes(), e.getLanguage(), e.getLicense(),
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
