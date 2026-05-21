package com.odin.catalog.ai.api.v1;

import com.odin.catalog.ai.api.v1.dto.MessageRequest;
import com.odin.catalog.ai.infrastructure.jpa.entity.ConversationEntity;
import com.odin.catalog.ai.infrastructure.jpa.repository.ConversationRepository;
import com.odin.catalog.ai.rag.ChatService;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@Tag(name = "Conversations", description = "Manage AI chat conversations and stream responses via Server-Sent Events. "
    + "Each conversation maintains history across messages. Responses stream token-by-token.")
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationsController {

    private final ConversationRepository conversationRepository;
    private final ChatService chatService;

    @Operation(summary = "List conversations",
        description = "Returns all AI conversations for the authenticated tenant, ordered by creation date (newest first).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of conversations"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping
    public List<ConversationEntity> list() {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        return conversationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Operation(summary = "Create a conversation",
        description = "Creates a new empty AI conversation. Optionally provide a title; defaults to 'New conversation'.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Conversation created"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Optional conversation title as a plain string",
        required = false,
        content = @Content(schema = @Schema(type = "string", example = "Credit risk datasets")))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationEntity create(@RequestBody(required = false) String title) {
        UUID tenantId = UUID.fromString(TenantContextHolder.get());
        ConversationEntity conv = new ConversationEntity();
        conv.setTenantId(tenantId);
        conv.setTitle(title != null ? title : "New conversation");
        return conversationRepository.save(conv);
    }

    @Operation(summary = "Get a conversation",
        description = "Returns a single conversation by UUID including its metadata.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conversation found"),
        @ApiResponse(responseCode = "404", description = "Conversation not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @GetMapping("/{id}")
    public ConversationEntity get(
            @Parameter(description = "Conversation UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return conversationRepository.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("Conversation not found: " + id));
    }

    @Operation(summary = "Send a message and stream the AI response",
        description = "Sends a user message and streams the AI response token-by-token as Server-Sent Events. "
            + "The RAG pipeline embeds the message, retrieves the top-10 relevant catalog documents, "
            + "and passes them as context to the LLM. "
            + "If focusDatasetId is provided, the AI first calls internal tools to fetch that dataset's logical model and vocabulary mappings. "
            + "Each SSE `data:` line contains one text token. The stream closes when the response is complete. "
            + "**Note**: Swagger UI's Try-it-out does not support SSE streaming — use curl or EventSource.")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "SSE token stream — each event is a text fragment",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                schema = @Schema(type = "string", example = "The Trade Positions dataset contains..."))),
        @ApiResponse(responseCode = "400", description = "Validation error — content is required", content = @Content),
        @ApiResponse(responseCode = "404", description = "Conversation not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessage(
            @Parameter(description = "Conversation UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody MessageRequest request) {
        return chatService.streamResponse(id, request);
    }
}
