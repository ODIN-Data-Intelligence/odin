package com.odin.catalog.shared.models.dcat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.odin.catalog.shared.models.common.PeriodOfTime;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DcatDataset(
    DcatResource resource,
    String accrualPeriodicity,      // Dublin Core frequency IRI
    PeriodOfTime temporal,
    String spatialResolutionInMeters,
    String temporalResolution,      // ISO-8601 duration
    String version,
    String versionNotes,
    String isVersionOf,             // parent dataset ID
    List<DcatDistribution> distributions,
    List<DcatDataService> dataServices
) {}
