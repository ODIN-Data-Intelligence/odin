package com.odin.catalog.identity.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Request body for updating a bookmark's collection or note")
public record BookmarkPatchRequest(
    @Schema(description = "Collection to move this bookmark into, or null to remove from any collection")
    UUID collectionId,

    @Schema(description = "Personal note about this dataset")
    String note
) {}
