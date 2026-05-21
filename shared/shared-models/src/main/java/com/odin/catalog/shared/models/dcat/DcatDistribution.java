package com.odin.catalog.shared.models.dcat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.odin.catalog.shared.models.common.SpdxChecksum;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DcatDistribution(
    DcatResource resource,
    String datasetId,
    String accessUrl,               // required by DCAT
    String downloadUrl,
    String mediaType,               // IANA media type
    String format,                  // DCTERMS format IRI
    Long byteSize,
    SpdxChecksum checksum,
    String compressFormat,
    String packageFormat,
    String availability,            // planned availability IRI
    String csvwTableId              // FK to csvw_tables (inventory-service local)
) {}
