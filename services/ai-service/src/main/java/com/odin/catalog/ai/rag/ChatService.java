package com.odin.catalog.ai.rag;

import com.odin.catalog.ai.api.v1.dto.MessageRequest;
import com.odin.catalog.ai.infrastructure.jpa.entity.ConversationMessageEntity;
import com.odin.catalog.ai.infrastructure.jpa.repository.ConversationMessageRepository;
import com.odin.catalog.ai.tools.DatasetContextService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ConversationMessageRepository messageRepository;
    private final DatasetContextService datasetContextService;

    public Flux<String> streamResponse(UUID conversationId, MessageRequest request) {
        long requestStart = System.currentTimeMillis();
        MDC.put("conversationId", conversationId.toString());
        try {
            return buildAndStream(conversationId, request, requestStart);
        } finally {
            // MDC cleared here — synchronous steps are complete; streaming is async
            MDC.remove("conversationId");
        }
    }

    private Flux<String> buildAndStream(UUID conversationId, MessageRequest request, long requestStart) {
        // ── Step 1: persist user message ─────────────────────────────────────
        log.info("step=USER_MESSAGE_RECEIVED content.length={} focusDatasetId={}",
            request.content().length(), request.focusDatasetId());
        saveMessage(conversationId, "user", request.content());

        // ── Step 2: focused tool-chain context ───────────────────────────────
        String focusedContext = "";
        if (request.focusDatasetId() != null && !request.focusDatasetId().isBlank()) {
            long t = System.currentTimeMillis();
            log.info("step=FOCUSED_CONTEXT_START datasetId={}", request.focusDatasetId());
            focusedContext = datasetContextService.buildFocusedContext(
                request.focusDatasetId(), request.content(), conversationId.toString()
            );
            log.info("step=FOCUSED_CONTEXT_COMPLETE datasetId={} result.length={} elapsed={}ms",
                request.focusDatasetId(), focusedContext.length(), elapsed(t));
        }

        // ── Step 3: RAG retrieval ────────────────────────────────────────────
        long t3 = System.currentTimeMillis();
        log.info("step=RAG_RETRIEVAL_START topK=8");
        List<Document> ragDocs = vectorStore.similaritySearch(
            SearchRequest.builder().query(request.content()).topK(8).build()
        );
        log.info("step=RAG_RETRIEVAL_COMPLETE docs.found={} elapsed={}ms",
            ragDocs.size(), elapsed(t3));
        if (log.isDebugEnabled()) {
            for (int i = 0; i < ragDocs.size(); i++) {
                log.debug("step=RAG_DOC index={} id={} preview={}",
                    i, ragDocs.get(i).getId(), preview(ragDocs.get(i).getText(), 120));
            }
        }
        String ragContext = ragDocs.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n---\n\n"));

        // ── Step 4: conversation history ─────────────────────────────────────
        List<Message> history = buildHistory(conversationId);
        log.info("step=HISTORY_LOADED messageCount={}", history.size());

        // ── Step 5: compose system prompt ────────────────────────────────────
        String systemPrompt = buildSystemPrompt(focusedContext, ragContext);
        log.info("step=LLM_STREAM_START systemPrompt.length={} historyMessages={}",
            systemPrompt.length(), history.size());

        // ── Step 6: stream LLM response ──────────────────────────────────────
        long streamStart = System.currentTimeMillis();
        StringBuilder fullResponse = new StringBuilder();
        return chatClient.prompt()
            .system(systemPrompt)
            .messages(history)
            .user(request.content())
            .stream()
            .content()
            .doOnNext(fullResponse::append)
            .doOnComplete(() -> {
                log.info("[conv={}] step=LLM_STREAM_COMPLETE response.length={} streamElapsed={}ms totalElapsed={}ms",
                    conversationId, fullResponse.length(),
                    System.currentTimeMillis() - streamStart,
                    System.currentTimeMillis() - requestStart);
                saveMessage(conversationId, "assistant", fullResponse.toString());
            })
            .doOnError(ex ->
                log.error("[conv={}] step=LLM_STREAM_ERROR error={}", conversationId, ex.getMessage())
            );
    }

    private String buildSystemPrompt(String focusedContext, String ragContext) {
        if (!focusedContext.isBlank()) {
            return """
                You are an AI assistant for a data catalog. Help users discover, understand, and use datasets.

                DATASET CURRENTLY BEING VIEWED BY THE USER:
                %s

                Use the above dataset as the primary context when relevant to the question. \
                If the question is clearly about something else, draw on the broader catalog context below.

                ADDITIONAL CATALOG CONTEXT:
                %s

                Be concise and helpful.
                """.formatted(focusedContext, ragContext);
        }
        return """
            You are an AI assistant for a data catalog. Help users discover, understand, and use datasets.

            Relevant catalog context:
            %s

            Answer based on the context above when relevant. Be concise and helpful.
            """.formatted(ragContext);
    }

    private void saveMessage(UUID conversationId, String role, String content) {
        ConversationMessageEntity msg = new ConversationMessageEntity();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        messageRepository.save(msg);
    }

    private List<Message> buildHistory(UUID conversationId) {
        return messageRepository
            .findByConversationIdOrderByCreatedAtAsc(conversationId)
            .stream()
            .map(msg -> (Message) switch (msg.getRole()) {
                case "user"      -> new UserMessage(msg.getContent());
                case "assistant" -> new AssistantMessage(msg.getContent());
                case "system"    -> new SystemMessage(msg.getContent());
                default          -> new UserMessage(msg.getContent());
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static long elapsed(long since) {
        return System.currentTimeMillis() - since;
    }

    private static String preview(String text, int maxLen) {
        if (text == null) return "(null)";
        String s = text.replace('\n', ' ');
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
