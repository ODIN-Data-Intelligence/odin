package com.odin.catalog.ai.application.memory;

import com.odin.catalog.ai.api.v1.dto.AgenticEvent.CombinedProposal;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent.ElementProposal;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent.ReviewComment;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent.VocabConceptProposal;
import com.odin.catalog.ai.client.CatalogServiceClient.LogicalElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Long-term memory for the agentic proposer/reviewer loop, backed by the shared Spring AI
 * {@link VectorStore} (pgvector). Memory lets the proposer learn across runs so it gets more right
 * on the first attempt and the loop converges in fewer iterations.
 *
 * <p>Two kinds of memory are stored, both tagged {@code entityType=AGENT_MEMORY} and scoped by
 * {@code tenantId} so they never leak into dataset RAG (which filters on {@code entityType=DATASET}):
 * <ul>
 *   <li><b>LESSON</b> — a reviewer issue that was raised during a run plus the finally-approved
 *       resolution (e.g. "a PII field classified INTERNAL must be raised to CONFIDENTIAL").</li>
 *   <li><b>EXEMPLAR</b> — the final, approved values for an element, written only when the run was
 *       APPROVED (we never teach from un-approved, max-iterations output).</li>
 * </ul>
 *
 * <p>Retrieval embeds a per-element signature and similarity-searches tenant-scoped memory. All
 * operations are best-effort: any failure is logged and swallowed so memory never breaks a review.
 */
@Service
public class AgentReviewMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AgentReviewMemoryService.class);

    static final String ENTITY_TYPE = "AGENT_MEMORY";
    static final String KIND_LESSON = "LESSON";
    static final String KIND_EXEMPLAR = "EXEMPLAR";
    /** Hard cap on lessons injected into a single prompt, regardless of element count. */
    private static final int GLOBAL_RETRIEVE_CAP = 15;

    private final VectorStore vectorStore;
    private final boolean enabled;
    private final int topK;

    public AgentReviewMemoryService(
            VectorStore vectorStore,
            @Value("${app.agentic.memory.enabled:true}") boolean enabled,
            @Value("${app.agentic.memory.top-k:3}") int topK) {
        this.vectorStore = vectorStore;
        this.enabled = enabled;
        this.topK = topK;
    }

    // ── Retrieval ────────────────────────────────────────────────────────────────

    /**
     * Returns de-duplicated lesson/exemplar texts relevant to the elements about to be proposed,
     * drawn from this tenant's past reviews. Empty when memory is disabled or nothing matches.
     */
    public List<String> retrieveLessons(String tenantId, List<LogicalElement> elements, List<String> themes) {
        if (!enabled || isBlank(tenantId) || elements == null || elements.isEmpty()) return List.of();
        String themeText = themeText(themes);
        String filter = "entityType == '" + ENTITY_TYPE + "' && tenantId == '" + tenantId + "'";
        LinkedHashSet<String> lessons = new LinkedHashSet<>();
        for (LogicalElement el : elements) {
            if (lessons.size() >= GLOBAL_RETRIEVE_CAP) break;
            try {
                List<Document> hits = vectorStore.similaritySearch(
                    SearchRequest.builder().query(signature(el.name(), el.logicalType(), themeText))
                        .topK(topK).filterExpression(filter).build());
                if (hits == null) continue;
                for (Document d : hits) {
                    if (d.getText() != null && !d.getText().isBlank()) lessons.add(d.getText().strip());
                    if (lessons.size() >= GLOBAL_RETRIEVE_CAP) break;
                }
            } catch (Exception e) {
                log.warn("action=AGENT_MEMORY_RETRIEVE_FAILED tenantId={} element={} error={}",
                    tenantId, el.name(), e.getMessage());
            }
        }
        if (!lessons.isEmpty()) {
            log.info("action=AGENT_MEMORY_RETRIEVED tenantId={} lessonCount={}", tenantId, lessons.size());
        }
        return new ArrayList<>(lessons);
    }

    // ── Recording ────────────────────────────────────────────────────────────────

    /**
     * Persists memory at the end of a run. Writes one EXEMPLAR per element (only when {@code approved})
     * and one LESSON per distinct reviewer issue encountered, with the finally-approved resolution.
     * Best-effort; never throws.
     *
     * @param issuesByElement reviewer comments accumulated across iterations, keyed by elementId
     */
    public void record(String tenantId, List<LogicalElement> elements, List<String> themes,
                       CombinedProposal finalProposal, Map<String, List<ReviewComment>> issuesByElement,
                       boolean approved) {
        if (!enabled || isBlank(tenantId) || finalProposal == null || finalProposal.elements() == null) return;
        try {
            String themeText = themeText(themes);
            Map<String, LogicalElement> byId = elements == null ? Map.of()
                : elements.stream().filter(e -> e.id() != null)
                    .collect(Collectors.toMap(LogicalElement::id, e -> e, (a, b) -> a));
            Map<String, List<ReviewComment>> issues = issuesByElement == null ? Map.of() : issuesByElement;

            List<Document> docs = new ArrayList<>();
            for (ElementProposal p : finalProposal.elements()) {
                if (p == null || p.elementId() == null) continue;
                LogicalElement el = byId.get(p.elementId());
                String name = el != null && el.name() != null ? el.name() : p.name();
                String type = el != null ? el.logicalType() : null;
                if (isBlank(name)) continue;

                if (approved) {
                    docs.add(exemplarDocument(tenantId, name, type, themeText, p));
                }
                Set<String> seen = new HashSet<>();
                for (ReviewComment c : issues.getOrDefault(p.elementId(), List.of())) {
                    if (c == null || isBlank(c.issue())) continue;
                    String dimension = c.dimension() == null ? "general" : c.dimension();
                    if (!seen.add(dimension + "|" + c.issue())) continue;
                    docs.add(lessonDocument(tenantId, name, type, themeText, dimension, c.issue(), p));
                }
            }
            if (!docs.isEmpty()) {
                vectorStore.add(docs);
                log.info("action=AGENT_MEMORY_RECORDED tenantId={} docCount={} approved={}",
                    tenantId, docs.size(), approved);
            }
        } catch (Exception e) {
            log.warn("action=AGENT_MEMORY_RECORD_FAILED tenantId={} error={}", tenantId, e.getMessage());
        }
    }

    // ── Document builders ──────────────────────────────────────────────────────────

    private Document exemplarDocument(String tenantId, String name, String type, String themeText, ElementProposal p) {
        String text = "Element '" + name + "'" + typeText(type) + themeText
            + ". Approved governance: classification=" + nz(p.classification())
            + ", isPersonalInformation=" + p.isPersonalInformation()
            + ", isDirectIdentifier=" + p.isDirectIdentifier()
            + ". Concepts: " + conceptIris(p)
            + (isBlank(p.description()) ? "" : ". Description: " + p.description());
        Map<String, Object> meta = baseMeta(tenantId, KIND_EXEMPLAR, "all", name, type);
        if (!isBlank(p.classification())) meta.put("classification", p.classification());
        meta.put("isPersonalInformation", String.valueOf(p.isPersonalInformation()));
        return new Document(memoryId(tenantId, name, type, KIND_EXEMPLAR, "all", null), text, meta);
    }

    private Document lessonDocument(String tenantId, String name, String type, String themeText,
                                    String dimension, String issue, ElementProposal p) {
        String text = "Element '" + name + "'" + typeText(type) + themeText
            + ". Reviewer issue [" + dimension + "]: " + issue
            + ". Resolution: " + resolution(dimension, p) + ".";
        Map<String, Object> meta = baseMeta(tenantId, KIND_LESSON, dimension, name, type);
        return new Document(memoryId(tenantId, name, type, KIND_LESSON, dimension, issue), text, meta);
    }

    private static Map<String, Object> baseMeta(String tenantId, String kind, String dimension, String name, String type) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("entityType", ENTITY_TYPE);
        meta.put("tenantId", tenantId);
        meta.put("memoryKind", kind);
        meta.put("dimension", dimension);
        meta.put("elementName", name);
        if (!isBlank(type)) meta.put("logicalType", type);
        return meta;
    }

    private static String resolution(String dimension, ElementProposal p) {
        return switch (dimension == null ? "" : dimension.toLowerCase()) {
            case "classification" -> "classification = " + nz(p.classification());
            case "pii" -> "isPersonalInformation=" + p.isPersonalInformation()
                + ", isDirectIdentifier=" + p.isDirectIdentifier();
            case "description" -> "description = " + nz(p.description());
            case "vocab" -> "vocabConcepts = " + conceptIris(p);
            default -> "classification=" + nz(p.classification())
                + ", isPersonalInformation=" + p.isPersonalInformation();
        };
    }

    private static String conceptIris(ElementProposal p) {
        if (p.vocabConcepts() == null || p.vocabConcepts().isEmpty()) return "(none)";
        return p.vocabConcepts().stream()
            .map(VocabConceptProposal::conceptIri)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(", "));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static String signature(String name, String type, String themeText) {
        return "Element '" + nz(name) + "'" + typeText(type) + themeText;
    }

    private static String themeText(List<String> themes) {
        if (themes == null || themes.isEmpty()) return "";
        return " in dataset about " + String.join(", ", themes);
    }

    private static String typeText(String type) {
        return isBlank(type) ? "" : " (type " + type + ")";
    }

    /** Stable id so re-runs upsert the same lesson/exemplar rather than duplicating it. */
    private static String memoryId(String tenantId, String name, String type, String kind, String dimension, String issue) {
        String key = tenantId + "|" + name + "|" + nz(type) + "|" + kind + "|" + dimension
            + (issue == null ? "" : "|" + issue);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
