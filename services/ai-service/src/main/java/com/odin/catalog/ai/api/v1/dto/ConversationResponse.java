package com.odin.catalog.ai.api.v1.dto;

import com.odin.catalog.ai.infrastructure.jpa.entity.ConversationEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConversationResponse(
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) UUID id,
    String title,
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) UUID tenantId,
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) OffsetDateTime createdAt
) {
    public static ConversationResponse from(ConversationEntity e) {
        return new ConversationResponse(e.getId(), e.getTitle(), e.getTenantId(), e.getCreatedAt());
    }
}
