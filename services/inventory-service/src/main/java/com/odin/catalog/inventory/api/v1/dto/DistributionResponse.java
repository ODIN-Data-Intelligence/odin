package com.odin.catalog.inventory.api.v1.dto;

import com.odin.catalog.inventory.infrastructure.jpa.entity.DistributionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "DCAT Distribution — a specific representation or access point for a dataset")
public record DistributionResponse(

    UUID id,
    UUID tenantId,
    UUID datasetId,
    String iri,
    String title,
    String description,
    String accessUrl,
    String downloadUrl,
    String mediaType,
    String format,
    Long byteSize,
    String checksumAlgorithm,
    String checksumValue,
    String compressFormat,
    String packageFormat,
    String availability,
    String databaseName,
    String schemaName,
    String tableName,
    UUID csvwTableId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt

) {
    public static DistributionResponse from(DistributionEntity e) {
        return new DistributionResponse(
            e.getId(), e.getTenantId(), e.getDatasetId(), e.getIri(),
            e.getTitle(), e.getDescription(), e.getAccessUrl(), e.getDownloadUrl(),
            e.getMediaType(), e.getFormat(), e.getByteSize(),
            e.getChecksumAlgorithm(), e.getChecksumValue(),
            e.getCompressFormat(), e.getPackageFormat(), e.getAvailability(),
            e.getDatabaseName(), e.getSchemaName(), e.getTableName(),
            e.getCsvwTableId(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
