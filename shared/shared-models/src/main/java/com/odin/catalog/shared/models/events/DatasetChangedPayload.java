package com.odin.catalog.shared.models.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.odin.catalog.shared.models.dcat.DcatDataset;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetChangedPayload(
    String changeType,   // CREATED, UPDATED, DELETED
    String datasetId,
    String catalogId,
    String domainId,
    String tenantId,
    DcatDataset dataset,
    // Optional semantic enrichment — populated from vocabulary mappings on logical elements.
    // Null when no logical model exists yet (e.g. immediately after dataset creation).
    List<String> semanticTypes,
    List<String> vocabConceptLabels,
    List<String> vocabConceptIris,
    List<String> fiboConcepts,
    List<String> logicalElementNames,
    List<String> logicalTypes
) {
    /** Convenience factory for events without semantic context (e.g. delete, or initial create). */
    public static DatasetChangedPayload ofBasic(String changeType, String datasetId,
            String catalogId, String domainId, String tenantId, DcatDataset dataset) {
        return new DatasetChangedPayload(changeType, datasetId, catalogId, domainId, tenantId,
                dataset, null, null, null, null, null, null);
    }
}
