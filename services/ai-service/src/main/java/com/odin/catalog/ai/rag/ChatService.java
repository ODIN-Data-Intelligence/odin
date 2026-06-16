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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedCollection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
        List<String> focusIds = resolveDatasetIds(request);
        log.info("step=USER_MESSAGE_RECEIVED content.length={} focusDatasetIds={}",
            request.content().length(), focusIds);
        saveMessage(conversationId, "user", request.content());

        // ── Step 2: focused tool-chain context ───────────────────────────────
        String focusedContext = "";
        if (!focusIds.isEmpty()) {
            long t = System.currentTimeMillis();
            log.info("step=FOCUSED_CONTEXT_START datasetCount={} ids={}", focusIds.size(), focusIds);
            focusedContext = datasetContextService.buildFocusedContext(
                focusIds, request.content(), conversationId.toString()
            );
            log.info("step=FOCUSED_CONTEXT_COMPLETE datasetCount={} result.length={} elapsed={}ms",
                focusIds.size(), focusedContext.length(), elapsed(t));
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
        Flux<String> raw = chatClient.prompt()
            .system(systemPrompt)
            .messages(history)
            .user(request.content())
            .stream()
            .content();
        return stripThinkBlocks(raw, fullResponse)
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

    /**
     * Strips {@code <think>...</think>} blocks emitted by reasoning models (e.g. qwen3)
     * before tokens reach the SSE client. Buffers tokens until the end of the think block
     * is confirmed, then emits the remainder normally. If no think block is detected
     * within the first {@code <think>} tag length of output, all buffered tokens are
     * flushed immediately.
     */
    private static Flux<String> stripThinkBlocks(Flux<String> raw, StringBuilder visible) {
        final String OPEN  = "<think>";
        final String CLOSE = "</think>";
        AtomicBoolean past = new AtomicBoolean(false);
        StringBuilder buf  = new StringBuilder();

        return raw.concatMap(token -> {
            if (past.get()) {
                visible.append(token);
                return Flux.just(token);
            }
            buf.append(token);
            String s = buf.toString();

            // No think block: buffer is longer than the open tag and doesn't start with it
            if (s.length() >= OPEN.length() && !s.startsWith(OPEN)) {
                past.set(true);
                buf.setLength(0);
                visible.append(s);
                return Flux.just(s);
            }

            // Think block closed — emit only what follows </think>
            int closeIdx = s.indexOf(CLOSE);
            if (closeIdx >= 0) {
                past.set(true);
                buf.setLength(0);
                // Strip leading whitespace/newlines that the model emits between </think> and its answer
                String after = s.substring(closeIdx + CLOSE.length()).replaceAll("^\\s+", "");
                if (!after.isEmpty()) {
                    visible.append(after);
                    return Flux.just(after);
                }
                return Flux.empty();
            }

            // Still buffering — either inside <think> or accumulating the opening tag
            return Flux.empty();
        });
    }

    /** Union of focusDatasetId (compat) + focusDatasetIds, deduplicated, blanks removed. */
    private static List<String> resolveDatasetIds(MessageRequest request) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (request.focusDatasetId() != null && !request.focusDatasetId().isBlank())
            ids.add(request.focusDatasetId());
        if (request.focusDatasetIds() != null)
            request.focusDatasetIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .forEach(ids::add);
        return new ArrayList<>(ids);
    }

    private String buildSystemPrompt(String focusedContext, String ragContext) {
        if (!focusedContext.isBlank()) {
            return """
                You are an AI assistant for a data catalog. Help users discover, understand, and use datasets.

                DATASET CONTEXT (schema, columns, and join hints for focused datasets):
                %s

                CRITICAL RULES — follow exactly, especially when writing SQL:
                - Only use table and column names that literally appear above. Never invent, guess, or assume \
                a column or table name that is not listed.
                - Text shown as "(business name: ...)" is for human reference only — it is NEVER a valid SQL \
                identifier. Always use the actual column name listed before it.
                - Only join two tables using a column pair from a line starting with "Suggested join:". If no \
                such line connects the tables you need, tell the user no verified relationship was found \
                instead of guessing one.
                - Never write a single SQL statement joining tables that are on different platforms — follow \
                the Target Platform / PLATFORM CONFLICT guidance above exactly.
                - If you are not confident a column, table, or join exists, say so explicitly rather than \
                producing a query that might be wrong.

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
