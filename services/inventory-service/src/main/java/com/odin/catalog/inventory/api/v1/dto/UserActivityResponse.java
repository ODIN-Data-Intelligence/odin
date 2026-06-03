package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "All activity involving the current user — proposals and dataset changes")
public record UserActivityResponse(

    @Schema(description = "Ownership proposals where the user is the proposer or the nominated owner")
    List<ActivityProposalResponse> proposals,

    @Schema(description = "Dataset changes made by the user, most recent first")
    List<ActivityChangeResponse> changes

) {}
