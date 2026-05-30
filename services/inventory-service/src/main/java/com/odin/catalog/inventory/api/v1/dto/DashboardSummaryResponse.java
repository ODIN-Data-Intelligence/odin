package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Dashboard summary for the current data owner")
public record DashboardSummaryResponse(

    @Schema(description = "Number of datasets owned by the current user")
    long ownedDatasetCount,

    @Schema(description = "Number of data products owned by the current user")
    long ownedDataProductCount,

    @Schema(description = "Pending ownership transfer proposals addressed to the current user")
    List<OwnershipProposalResponse> pendingTransferRequests

) {}
