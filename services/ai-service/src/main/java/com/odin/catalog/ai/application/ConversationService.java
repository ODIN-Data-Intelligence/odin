package com.odin.catalog.ai.application;

import com.odin.catalog.ai.api.v1.dto.ConversationResponse;
import com.odin.catalog.ai.infrastructure.jpa.entity.ConversationEntity;
import com.odin.catalog.ai.infrastructure.jpa.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public List<ConversationResponse> list(UUID tenantId) {
        return conversationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
            .stream().map(ConversationResponse::from).toList();
    }

    public ConversationResponse get(UUID id) {
        return conversationRepository.findById(id)
            .map(ConversationResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Conversation not found: " + id));
    }

    public ConversationResponse create(UUID tenantId, String title) {
        ConversationEntity conv = new ConversationEntity();
        conv.setTenantId(tenantId);
        conv.setTitle(title != null ? title : "New conversation");
        return ConversationResponse.from(conversationRepository.save(conv));
    }
}
