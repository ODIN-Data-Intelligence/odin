package com.odin.catalog.inventory.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Optional note when approving or rejecting an ownership proposal")
public record ResolveProposalRequest(

    @Schema(description = "Optional note explaining the decision", example = "Confirmed with the trading desk lead")
    String note

) {}
