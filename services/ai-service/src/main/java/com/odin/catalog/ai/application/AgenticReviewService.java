package com.odin.catalog.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent.CombinedProposal;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent.ElementProposal;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent.ReviewComment;
import com.odin.catalog.ai.api.v1.dto.AgenticEvent.VocabConceptProposal;
import com.odin.catalog.ai.application.memory.AgentReviewMemoryService;
import com.odin.catalog.ai.client.CatalogServiceClient;
import com.odin.catalog.ai.client.CatalogServiceClient.AgenticRecommendationsPayload;
import com.odin.catalog.ai.client.CatalogServiceClient.ElementRecommendation;
import com.odin.catalog.ai.client.CatalogServiceClient.VocabConcept;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Orchestrates the agentic proposer/reviewer loop for one logical model.
 *
 * <p>A <b>proposer</b> agent emits one combined proposal covering description, classification,
 * vocabulary concepts and PII/DII for every element. A <b>reviewer</b> agent then audits that
 * proposal against the <b>full DCAT dataset</b> (metadata, distributions and every element with
 * its currently-accepted values) and returns APPROVE or REJECT with per-issue comments. A
 * rejection is fed back to the proposer for a revised proposal. The loop is capped at
 * {@link #MAX_ITERATIONS}; on exhaustion the best-so-far proposal is shipped anyway.
 *
 * <p>Each phase is pushed to the caller as an SSE event the moment it completes. The loop is
 * driven from the {@link Flux} subscription on a virtual-thread scheduler, so the LLM round-trips
 * never block the servlet request thread and emissions begin only once the response is committed.
 */
@Service
@RequiredArgsConstructor
public class AgenticReviewService {

    private static final Logger log = LoggerFactory.getLogger(AgenticReviewService.class);
    static final int MAX_ITERATIONS = 15;
    /**
     * One-directional dependency order (a DAG) used to break the cross-dimension oscillation:
     * meaning → privacy → sensitivity → narrative. A dimension is locked once the reviewer stops
     * flagging it; a later (downstream) revision can never reopen an earlier (upstream) one.
     */
    static final List<String> DIMENSION_ORDER = List.of("vocab", "pii", "classification", "description");
    /** Progress guard: force-lock the contested dimension after this many stuck rounds on it. */
    static final int MAX_ATTEMPTS_PER_FRONTIER = 3;
    private static final long LLM_TIMEOUT_MINUTES = 5L;
    private static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final CatalogServiceClient catalogClient;
    private final AgentReviewMemoryService memoryService;

    public Flux<String> review(UUID datasetId, UUID modelId, String authHeader) {
        // Drive the blocking loop from the subscription (not an eagerly-spawned thread) so emissions
        // begin only after Spring MVC has committed the SSE response — avoids a race with the servlet
        // async writer. subscribeOn a virtual thread keeps the long LLM round-trips off the request thread.
        return Flux.<String>create(sink -> runLoop(datasetId, modelId, authHeader, sink))
            .subscribeOn(Schedulers.fromExecutor(VIRTUAL_EXECUTOR));
    }

    private void runLoop(UUID datasetId, UUID modelId, String authHeader, FluxSink<String> sink) {
        long t = System.currentTimeMillis();
        log.info("action=AGENTIC_REVIEW_START datasetId={} modelId={}", datasetId, modelId);
        try {
            emit(sink, AgenticEvent.marker("CONTEXT", 0));
            ReviewContext ctx = buildContext(datasetId, modelId, authHeader);
            if (ctx.elements().isEmpty()) {
                emit(sink, AgenticEvent.error(0, "This logical model has no elements to review."));
                sink.complete();
                return;
            }

            // Long-term memory: pull lessons from this tenant's past reviews so the proposer starts
            // closer to an approvable answer (fewer reject/revise loops).
            String tenantId = ctx.dataset() != null ? ctx.dataset().tenantId() : null;
            List<String> themes = ctx.dataset() != null ? ctx.dataset().themes() : List.of();
            List<String> lessons = memoryService.retrieveLessons(tenantId, ctx.elements(), themes);
            if (!lessons.isEmpty()) {
                emit(sink, AgenticEvent.message("MEMORY",
                    "Applied " + lessons.size() + " lesson(s) from past reviews"));
            }

            // Cumulative, per-element feedback ledger — every reviewer comment is retained across
            // iterations (not just the latest) so the proposer never regresses a fix or re-submits a
            // value the reviewer already rejected.
            Map<String, List<ElementAttempt>> ledger = new LinkedHashMap<>();
            // Ordered-locking state: dimensions settle in DIMENSION_ORDER and, once locked, are frozen
            // server-side and never reopened — this is what breaks the cross-dimension cycle.
            Set<String> lockedDimensions = new LinkedHashSet<>();
            CombinedProposal accepted = null;   // running proposal whose locked-dimension values are authoritative
            String frontier = null;             // dimension currently being contested
            int frontierAttempts = 0;           // stuck-rounds on the current frontier (progress guard)
            CombinedProposal lastProposal = null;
            for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
                // Stop burning LLM calls if the client (e.g. the dialog) has disconnected.
                if (sink.isCancelled()) {
                    log.info("action=AGENTIC_REVIEW_CANCELLED modelId={} iteration={}", modelId, iteration);
                    return;
                }
                emit(sink, AgenticEvent.marker("PROPOSING", iteration));
                String proposerRaw = callLlm(buildProposerPrompt(
                    ctx, ledger, iteration == 1 ? lessons : List.of(), lockedDimensions, accepted));
                CombinedProposal proposal = parseProposal(proposerRaw, ctx);
                // Freeze locked dimensions to their accepted values regardless of what the LLM returned.
                proposal = mergeLocked(proposal, accepted, lockedDimensions);
                accepted = proposal;
                lastProposal = proposal;
                emit(sink, AgenticEvent.proposal(iteration, proposal));

                emit(sink, AgenticEvent.marker("REVIEWING", iteration));
                String reviewerRaw = callLlm(buildReviewerPrompt(ctx, proposal, lockedDimensions));
                Verdict verdict = parseVerdict(reviewerRaw);
                // Drop comments on already-settled (locked) dimensions — they cannot be reopened.
                List<ReviewComment> active = verdict.comments().stream()
                    .filter(c -> !lockedDimensions.contains(normalizeDimension(c.dimension())))
                    .toList();
                emit(sink, AgenticEvent.review(iteration, verdict.approved() ? "APPROVE" : "REJECT",
                    verdict.comments(), verdict.summary()));

                Set<String> open = openDimensions(active);
                if (verdict.approved() || open.isEmpty()) {
                    persist(modelId, proposal, authHeader);
                    recordMemory(tenantId, ctx, proposal, ledger, true);
                    emit(sink, AgenticEvent.terminal("DONE", iteration, proposal));
                    log.info("action=AGENTIC_REVIEW_COMPLETE modelId={} iterations={} locked={} elapsed={}ms",
                        modelId, iteration, lockedDimensions, System.currentTimeMillis() - t);
                    sink.complete();
                    return;
                }

                // Everything upstream of the highest-precedence open dimension has no issues → lock it.
                String newFrontier = firstInOrder(open);
                List<String> nowLocked = new ArrayList<>();
                for (String dim : DIMENSION_ORDER) {
                    if (dim.equals(newFrontier)) break;
                    if (lockedDimensions.add(dim)) nowLocked.add(dim);
                }

                // Progress guard: if the same dimension stays contested too long, force-lock it and advance.
                if (newFrontier.equals(frontier)) {
                    if (++frontierAttempts >= MAX_ATTEMPTS_PER_FRONTIER) {
                        if (lockedDimensions.add(newFrontier)) nowLocked.add(newFrontier + " (forced)");
                        frontier = null;
                        frontierAttempts = 0;
                    }
                } else {
                    frontier = newFrontier;
                    frontierAttempts = 1;
                }
                if (!nowLocked.isEmpty()) {
                    emit(sink, AgenticEvent.message("LOCKED", "Settled & locked: " + String.join(", ", nowLocked)));
                }

                // Accumulate this iteration's active (non-locked) rejections for the next proposer prompt.
                appendLedger(ledger, iteration, proposal, active);
            }

            // Loop exhausted without convergence — ship the best-so-far proposal for owner review.
            if (lastProposal != null) {
                persist(modelId, lastProposal, authHeader);
                recordMemory(tenantId, ctx, lastProposal, ledger, false);
            }
            emit(sink, AgenticEvent.terminal("MAX_REACHED", MAX_ITERATIONS, lastProposal));
            log.info("action=AGENTIC_REVIEW_MAX_REACHED modelId={} iterations={} elapsed={}ms",
                modelId, MAX_ITERATIONS, System.currentTimeMillis() - t);
            sink.complete();
        } catch (Exception e) {
            log.error("action=AGENTIC_REVIEW_FAILED datasetId={} modelId={} elapsed={}ms error={}",
                datasetId, modelId, System.currentTimeMillis() - t, e.getMessage(), e);
            emit(sink, AgenticEvent.error(0, "Agentic review failed: "
                + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
            sink.complete();
        }
    }

    // ── Context ────────────────────────────────────────────────────────────────

    private ReviewContext buildContext(UUID datasetId, UUID modelId, String authHeader) {
        CatalogServiceClient.DatasetSummary dataset = catalogClient.getDataset(datasetId.toString(), authHeader);
        List<CatalogServiceClient.LogicalElement> elements = catalogClient.getLogicalElements(modelId.toString(), authHeader);
        List<CatalogServiceClient.Distribution> distributions = catalogClient.getDistributions(datasetId.toString(), authHeader);
        List<CatalogServiceClient.Vocabulary> vocabularies = catalogClient.getVocabularies(authHeader);
        return new ReviewContext(dataset, elements == null ? List.of() : elements,
            distributions == null ? List.of() : distributions,
            vocabularies == null ? List.of() : vocabularies);
    }

    // ── LLM call (bounded, like the other recommendation services) ───────────────

    private String callLlm(String prompt) {
        return CompletableFuture
            .supplyAsync(() -> chatClient.prompt().system("/no_think").user(prompt).call().content(), VIRTUAL_EXECUTOR)
            .orTimeout(LLM_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .join();
    }

    // ── Proposer ────────────────────────────────────────────────────────────────

    private String buildProposerPrompt(ReviewContext ctx, Map<String, List<ElementAttempt>> ledger,
                                       List<String> lessons, Set<String> lockedDimensions, CombinedProposal accepted) {
        StringBuilder revision = new StringBuilder();

        // Dependency order: dimensions are decided as a one-directional pipeline and never revised
        // backwards — this is what prevents the classification⇄vocab⇄pii⇄description oscillation.
        revision.append("\nDecide the four dimensions in this fixed dependency order and NEVER change an ")
            .append("earlier (upstream) dimension to fit a later (downstream) one:\n")
            .append("  1. vocab (intrinsic meaning)  2. pii (derived from meaning)  ")
            .append("3. classification (derived from pii + meaning)  4. description (narrative).\n");

        // Locked dimensions are already approved — they must be reproduced verbatim and not changed.
        if (lockedDimensions != null && !lockedDimensions.isEmpty()) {
            revision.append("\nThese dimensions are APPROVED and LOCKED — reproduce their values EXACTLY and do ")
                .append("NOT change them: ").append(String.join(", ", lockedDimensions)).append('\n');
            String lockedValues = lockedValuesBlock(accepted, lockedDimensions);
            if (!lockedValues.isBlank()) revision.append(lockedValues);
            List<String> editable = DIMENSION_ORDER.stream().filter(d -> !lockedDimensions.contains(d)).toList();
            revision.append("Only revise these dimension(s): ").append(String.join(", ", editable)).append('\n');
        }

        // Advisory cross-run guidance from long-term memory (only injected on the first attempt).
        if (lessons != null && !lessons.isEmpty()) {
            revision.append("\nLessons from prior reviews of similar elements (advisory — apply where relevant):\n");
            for (String l : lessons) revision.append("  - ").append(l).append('\n');
        }

        // Hard, cumulative feedback from THIS run: every rejected attempt, so the proposer does not
        // regress an earlier fix or re-submit a value the reviewer already refused.
        if (ledger != null && !ledger.isEmpty()) {
            revision.append("\nThe REVIEWER has rejected previous proposals in this review. ")
                .append("Below is every issue raised so far and the value you proposed — produce a revised ")
                .append("proposal that resolves ALL of them and does NOT repeat any rejected value:\n");
            for (Map.Entry<String, List<ElementAttempt>> entry : ledger.entrySet()) {
                for (ElementAttempt a : entry.getValue()) {
                    for (ReviewComment c : a.issues()) {
                        revision.append("  - elementId ").append(entry.getKey())
                            .append(" (attempt ").append(a.iteration());
                        if (a.rejectedClassification() != null) {
                            revision.append(", you proposed classification=").append(a.rejectedClassification());
                        }
                        revision.append(") REJECTED [").append(c.dimension()).append("]: ").append(c.issue()).append('\n');
                    }
                }
            }
        }

        return """
            You are a data governance expert preparing a single combined recommendation for every
            data element of a logical model. For EACH element provide all four of:
              1. description        — a 1-2 sentence business description grounded in the element's vocabulary concepts.
              2. classification     — exactly one of PUBLIC, INTERNAL, CONFIDENTIAL, HIGH_CONFIDENTIAL.
              3. vocabConcepts      — up to 5 SKOS concept mappings drawn ONLY from the available vocabularies.
              4. isPersonalInformation / isDirectIdentifier — booleans about whether the field is about a natural person.

            Classification signals:
            - FIBO financial concepts (fibo-fnd, fibo-fbc, fibo-sec) -> CONFIDENTIAL unless aggregate/public market data
            - schema.org Person, email, telephone, identifier -> CONFIDENTIAL or HIGH_CONFIDENTIAL
            - credential, password, biometric, SSN, card number -> HIGH_CONFIDENTIAL
            - public market prices, public company names -> PUBLIC
            - aggregate statistics, operational metrics, reference codes -> INTERNAL
            - any dpv-pd: concept -> CONFIDENTIAL minimum; dpv-pd special categories (HealthData, Biometric,
              GeneticData, PoliticalOpinion, SexualLifeData, ReligiousBelief, RacialEthnicOrigin,
              TradeUnionMembership) -> HIGH_CONFIDENTIAL

            PII signals:
            - isPersonalInformation is true ONLY when the data is ABOUT a natural person (name, email, address,
              date of birth, government id). Trade ids, product codes and prices are NOT personal information.
            - isDirectIdentifier is true for fields that directly identify a person (email, SSN, passport, nationalId).

            Vocabulary mapping rules:
            - Use "exactMatch" for a precise concept, "closeMatch" for near-matches, "relatedMatch" for broader/narrower.
            - Construct full IRIs by appending the concept to the vocabulary baseIri exactly: path-style bases (ending /)
              -> baseIri + Concept/Path; fragment-style bases (ending #) -> baseIri + ConceptName.
            - Only use IRIs from the available vocabularies below. Prefer listed knownConcepts over guessing.

            Dataset context:
            %s

            Available vocabularies:
            %s

            Elements to analyse:
            %s
            %s
            Output rules (follow strictly):
            - Respond with a JSON array only. No markdown fences, no commentary outside the JSON.
            - Emit exactly one JSON object per element, each shaped:
              {"elementId":"...","description":"...","descriptionReasoning":"...",
               "classification":"CONFIDENTIAL","classificationReasoning":"...",
               "vocabConcepts":[{"conceptIri":"...","conceptLabel":"...","conceptDefinition":"...","matchType":"exactMatch","reasoning":"..."}],
               "isPersonalInformation":false,"isDirectIdentifier":false,"piiReasoning":"..."}
            - Every reasoning field must be a non-empty sentence. vocabConcepts may be an empty array when nothing fits.
            """.formatted(datasetBlock(ctx), vocabBlock(ctx), elementBlock(ctx), revision.toString());
    }

    // ── Reviewer ────────────────────────────────────────────────────────────────

    private String buildReviewerPrompt(ReviewContext ctx, CombinedProposal proposal, Set<String> lockedDimensions) {
        String proposalJson;
        try {
            proposalJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(proposal);
        } catch (Exception e) {
            proposalJson = "{}";
        }

        String lockedNote = (lockedDimensions == null || lockedDimensions.isEmpty())
            ? ""
            : "\nThe following dimensions are APPROVED and LOCKED — treat them as ground truth and do NOT "
                + "comment on them: " + String.join(", ", lockedDimensions) + ".\n";

        return """
            You are a senior data steward auditing a proposed set of recommendations against the WHOLE dataset,
            not just the element list. Judge whether the proposal is consistent, correct and complete.

            The four dimensions follow a one-directional dependency order: vocab -> pii -> classification ->
            description. If two dimensions are inconsistent, the DOWNSTREAM (later) one is at fault — request a
            change to it, NEVER to an upstream/earlier dimension. Do not ask to revise vocab to fit a
            classification, or a classification to fit a description.
            %s
            Check especially for:
            - classification that ignores a PII finding (personal data must be CONFIDENTIAL or higher).
            - vocabulary IRIs that are not built from one of the available vocabularies.
            - descriptions that contradict the element name, logical type, distributions, or other elements.
            - missing elements or empty/again-needed reasoning.

            Approve when the proposal is sound. Only reject for substantive problems — if the only remaining
            issues are trivial wording, APPROVE so the review terminates.

            === FULL DATASET CONTEXT ===
            %s

            Distributions:
            %s

            All elements (with currently-accepted values):
            %s

            === PROPOSAL UNDER REVIEW ===
            %s

            Output rules (follow strictly):
            - Respond with a single JSON object only. No markdown fences, no commentary outside the JSON.
            - Shape: {"verdict":"APPROVE"|"REJECT","summary":"one sentence","comments":[{"elementId":"...","dimension":"classification|description|vocab|pii","issue":"..."}]}
            - On APPROVE, "comments" may be an empty array. On REJECT, include one comment per problem found.
            """.formatted(lockedNote, datasetBlock(ctx), distributionBlock(ctx), fullElementBlock(ctx), proposalJson);
    }

    // ── Prompt block builders ────────────────────────────────────────────────────

    private String datasetBlock(ReviewContext ctx) {
        CatalogServiceClient.DatasetSummary d = ctx.dataset();
        if (d == null) return "(dataset metadata unavailable)";
        StringBuilder sb = new StringBuilder();
        if (d.title() != null) sb.append("Title: ").append(d.title()).append('\n');
        if (d.description() != null) sb.append("Description: ").append(d.description()).append('\n');
        if (d.keywords() != null && !d.keywords().isEmpty()) sb.append("Keywords: ").append(String.join(", ", d.keywords())).append('\n');
        if (d.themes() != null && !d.themes().isEmpty()) sb.append("Themes: ").append(String.join(", ", d.themes())).append('\n');
        return sb.toString().strip();
    }

    private String vocabBlock(ReviewContext ctx) {
        if (ctx.vocabularies().isEmpty()) return "(none registered — use exactMatch only when confident)";
        return ctx.vocabularies().stream().map(v -> {
            String line = "  - prefix: " + v.prefix() + "  baseIri: " + v.baseIri() + "  name: " + v.name();
            if (v.conceptHints() != null && !v.conceptHints().isBlank()) line += "\n    knownConcepts: " + v.conceptHints();
            return line;
        }).collect(Collectors.joining("\n"));
    }

    private String elementBlock(ReviewContext ctx) {
        return ctx.elements().stream().map(el -> {
            StringBuilder sb = new StringBuilder();
            sb.append("- elementId: ").append(el.id()).append('\n');
            sb.append("  name: ").append(el.name()).append('\n');
            if (el.label() != null) sb.append("  label: ").append(el.label()).append('\n');
            if (el.logicalType() != null) sb.append("  logicalType: ").append(el.logicalType()).append('\n');
            if (el.description() != null) sb.append("  description: ").append(el.description()).append('\n');
            List<String> labels = conceptLabels(el);
            if (!labels.isEmpty()) sb.append("  existingConcepts: ").append(String.join(", ", labels)).append('\n');
            return sb.toString();
        }).collect(Collectors.joining("\n"));
    }

    private String fullElementBlock(ReviewContext ctx) {
        return ctx.elements().stream().map(el -> {
            StringBuilder sb = new StringBuilder();
            sb.append("- elementId: ").append(el.id()).append("  name: ").append(el.name());
            if (el.logicalType() != null) sb.append("  type: ").append(el.logicalType());
            if (el.classification() != null) sb.append("  acceptedClassification: ").append(el.classification());
            sb.append("  isIdentifier: ").append(el.isIdentifier());
            sb.append("  isPersonalInformation: ").append(el.isPersonalInformation());
            sb.append("  isDirectIdentifier: ").append(el.isDirectIdentifier());
            List<String> labels = conceptLabels(el);
            if (!labels.isEmpty()) sb.append("  concepts: ").append(String.join(", ", labels));
            return sb.toString();
        }).collect(Collectors.joining("\n"));
    }

    private String distributionBlock(ReviewContext ctx) {
        if (ctx.distributions().isEmpty()) return "(no distributions)";
        return ctx.distributions().stream().map(dist -> {
            StringBuilder sb = new StringBuilder("- ");
            sb.append(dist.title() != null ? dist.title() : dist.id());
            if (dist.format() != null) sb.append(" [").append(dist.format()).append(']');
            if (dist.tableName() != null) sb.append("  table: ").append(dist.tableName());
            if (dist.accessUrl() != null) sb.append("  accessUrl: ").append(dist.accessUrl());
            return sb.toString();
        }).collect(Collectors.joining("\n"));
    }

    private List<String> conceptLabels(CatalogServiceClient.LogicalElement el) {
        if (el.vocabMappings() == null) return List.of();
        return el.vocabMappings().stream()
            .map(m -> m.conceptLabel() != null ? m.conceptLabel() : m.conceptIri())
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private CombinedProposal parseProposal(String raw, ReviewContext ctx) {
        Map<String, ElementProposal> byId = new LinkedHashMap<>();
        for (JsonNode node : JsonExtractionSupport.extractObjects(raw, objectMapper)) {
            String elementId = node.path("elementId").asText(null);
            if (elementId == null || node.has("verdict")) continue; // skip non-element objects
            List<VocabConceptProposal> concepts = new ArrayList<>();
            JsonNode conceptsNode = node.path("vocabConcepts");
            if (conceptsNode.isArray()) {
                for (JsonNode c : conceptsNode) {
                    String iri = c.path("conceptIri").asText(null);
                    if (iri == null) continue;
                    concepts.add(new VocabConceptProposal(
                        iri,
                        c.path("conceptLabel").asText(null),
                        c.path("conceptDefinition").asText(null),
                        c.path("matchType").asText("exactMatch"),
                        c.path("reasoning").asText(null)));
                }
            }
            byId.put(elementId, new ElementProposal(
                elementId,
                elementName(ctx, elementId),
                textOrNull(node, "description"),
                textOrNull(node, "descriptionReasoning"),
                textOrNull(node, "classification"),
                textOrNull(node, "classificationReasoning"),
                concepts,
                node.has("isPersonalInformation") ? node.path("isPersonalInformation").asBoolean() : null,
                node.has("isDirectIdentifier") ? node.path("isDirectIdentifier").asBoolean() : null,
                textOrNull(node, "piiReasoning")));
        }
        return new CombinedProposal(new ArrayList<>(byId.values()));
    }

    private Verdict parseVerdict(String raw) {
        for (JsonNode node : JsonExtractionSupport.extractObjects(raw, objectMapper)) {
            if (!node.has("verdict")) continue;
            boolean approved = "APPROVE".equalsIgnoreCase(node.path("verdict").asText(""));
            List<ReviewComment> comments = new ArrayList<>();
            JsonNode commentsNode = node.path("comments");
            if (commentsNode.isArray()) {
                for (JsonNode c : commentsNode) {
                    comments.add(new ReviewComment(
                        textOrNull(c, "elementId"),
                        c.path("dimension").asText("general"),
                        textOrNull(c, "issue")));
                }
            }
            return new Verdict(approved, comments, textOrNull(node, "summary"));
        }
        // Unparseable reviewer output — treat as a reject with a synthetic comment so the loop revises.
        log.warn("Could not parse reviewer verdict; treating as REJECT. raw={}", raw);
        return new Verdict(false,
            List.of(new ReviewComment(null, "general", "Reviewer response could not be parsed; please re-state the proposal clearly.")),
            "Unparseable reviewer response.");
    }

    private String elementName(ReviewContext ctx, String elementId) {
        return ctx.elements().stream().filter(e -> elementId.equals(e.id())).findFirst()
            .map(e -> e.label() != null ? e.label() : e.name()).orElse(null);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText(null);
    }

    // ── Persist ─────────────────────────────────────────────────────────────────

    private void persist(UUID modelId, CombinedProposal proposal, String authHeader) {
        List<ElementRecommendation> recs = proposal.elements().stream().map(p -> new ElementRecommendation(
            p.elementId(), p.description(), p.descriptionReasoning(),
            p.classification(), p.classificationReasoning(),
            p.vocabConcepts() == null ? List.of() : p.vocabConcepts().stream()
                .map(c -> new VocabConcept(c.conceptIri(), c.conceptLabel(), c.conceptDefinition(), c.matchType(), c.reasoning()))
                .toList(),
            p.isPersonalInformation(), p.isDirectIdentifier(), p.piiReasoning()
        )).toList();
        try {
            catalogClient.applyAgenticRecommendations(modelId.toString(), new AgenticRecommendationsPayload(recs), authHeader);
            log.info("action=AGENTIC_REVIEW_PERSISTED modelId={} elementCount={}", modelId, recs.size());
        } catch (Exception e) {
            log.error("action=AGENTIC_REVIEW_PERSIST_FAILED modelId={} error={}", modelId, e.getMessage());
            throw e;
        }
    }

    // ── Dimension locking ─────────────────────────────────────────────────────────

    /** Maps a reviewer comment's dimension onto one of {@link #DIMENSION_ORDER}; unknown -> "description". */
    private static String normalizeDimension(String dimension) {
        if (dimension == null) return "description";
        String d = dimension.toLowerCase();
        return DIMENSION_ORDER.contains(d) ? d : "description";
    }

    /** Distinct recognized dimensions still carrying open issues. */
    private static Set<String> openDimensions(List<ReviewComment> active) {
        Set<String> open = new LinkedHashSet<>();
        for (ReviewComment c : active) open.add(normalizeDimension(c.dimension()));
        return open;
    }

    /** The highest-precedence (earliest in {@link #DIMENSION_ORDER}) dimension among {@code open}. */
    private static String firstInOrder(Set<String> open) {
        for (String dim : DIMENSION_ORDER) if (open.contains(dim)) return dim;
        return DIMENSION_ORDER.get(DIMENSION_ORDER.size() - 1);
    }

    /**
     * Returns a copy of {@code incoming} where every locked dimension's fields are taken from
     * {@code accepted} (the last authoritative proposal) so locked values cannot drift, whatever the
     * LLM produced. Elements absent from {@code accepted} pass through unchanged.
     */
    private CombinedProposal mergeLocked(CombinedProposal incoming, CombinedProposal accepted, Set<String> locked) {
        if (accepted == null || locked.isEmpty()) return incoming;
        Map<String, ElementProposal> acceptedById = accepted.elements().stream()
            .filter(e -> e.elementId() != null)
            .collect(Collectors.toMap(ElementProposal::elementId, e -> e, (a, b) -> a, LinkedHashMap::new));
        List<ElementProposal> merged = incoming.elements().stream().map(in -> {
            ElementProposal a = acceptedById.get(in.elementId());
            if (a == null) return in;
            return new ElementProposal(
                in.elementId(),
                in.name(),
                locked.contains("description") ? a.description() : in.description(),
                locked.contains("description") ? a.descriptionReasoning() : in.descriptionReasoning(),
                locked.contains("classification") ? a.classification() : in.classification(),
                locked.contains("classification") ? a.classificationReasoning() : in.classificationReasoning(),
                locked.contains("vocab") ? a.vocabConcepts() : in.vocabConcepts(),
                locked.contains("pii") ? a.isPersonalInformation() : in.isPersonalInformation(),
                locked.contains("pii") ? a.isDirectIdentifier() : in.isDirectIdentifier(),
                locked.contains("pii") ? a.piiReasoning() : in.piiReasoning());
        }).toList();
        return new CombinedProposal(merged);
    }

    /** Renders the current locked-dimension values per element so the proposer can reproduce them verbatim. */
    private String lockedValuesBlock(CombinedProposal accepted, Set<String> locked) {
        if (accepted == null || locked.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ElementProposal e : accepted.elements()) {
            StringBuilder vals = new StringBuilder();
            if (locked.contains("vocab")) {
                String iris = e.vocabConcepts() == null ? "" : e.vocabConcepts().stream()
                    .map(VocabConceptProposal::conceptIri).filter(java.util.Objects::nonNull)
                    .collect(Collectors.joining(", "));
                vals.append(" vocab=[").append(iris).append(']');
            }
            if (locked.contains("pii")) {
                vals.append(" isPersonalInformation=").append(e.isPersonalInformation())
                    .append(" isDirectIdentifier=").append(e.isDirectIdentifier());
            }
            if (locked.contains("classification")) vals.append(" classification=").append(e.classification());
            if (locked.contains("description")) vals.append(" description=").append(e.description());
            if (!vals.isEmpty()) sb.append("  - elementId ").append(e.elementId()).append(':').append(vals).append('\n');
        }
        return sb.toString();
    }

    // ── Feedback ledger + memory ──────────────────────────────────────────────────

    /** Appends this iteration's reviewer comments (correlated with what was proposed) to the ledger. */
    private void appendLedger(Map<String, List<ElementAttempt>> ledger, int iteration,
                              CombinedProposal proposal, List<ReviewComment> comments) {
        if (comments == null || comments.isEmpty()) return;
        Map<String, ElementProposal> byId = proposal.elements().stream()
            .filter(e -> e.elementId() != null)
            .collect(Collectors.toMap(ElementProposal::elementId, e -> e, (a, b) -> a, LinkedHashMap::new));

        Map<String, List<ReviewComment>> byElement = new LinkedHashMap<>();
        for (ReviewComment c : comments) {
            byElement.computeIfAbsent(c.elementId() == null ? "(general)" : c.elementId(),
                k -> new ArrayList<>()).add(c);
        }
        byElement.forEach((elementId, issues) -> {
            ElementProposal p = byId.get(elementId);
            ledger.computeIfAbsent(elementId, k -> new ArrayList<>()).add(new ElementAttempt(
                iteration,
                p != null ? p.classification() : null,
                p != null ? p.isPersonalInformation() : null,
                issues));
        });
    }

    /** Flattens the ledger to reviewer comments per element and persists run memory (best-effort). */
    private void recordMemory(String tenantId, ReviewContext ctx, CombinedProposal finalProposal,
                              Map<String, List<ElementAttempt>> ledger, boolean approved) {
        Map<String, List<ReviewComment>> issuesByElement = new LinkedHashMap<>();
        ledger.forEach((elementId, attempts) -> issuesByElement.put(elementId,
            attempts.stream().flatMap(a -> a.issues().stream()).toList()));
        List<String> themes = ctx.dataset() != null ? ctx.dataset().themes() : List.of();
        memoryService.record(tenantId, ctx.elements(), themes, finalProposal, issuesByElement, approved);
    }

    private void emit(FluxSink<String> sink, AgenticEvent event) {
        try {
            sink.next(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Failed to serialize agentic event phase={}: {}", event.phase(), e.getMessage());
        }
    }

    // ── Internal holders ─────────────────────────────────────────────────────────

    private record ReviewContext(
        CatalogServiceClient.DatasetSummary dataset,
        List<CatalogServiceClient.LogicalElement> elements,
        List<CatalogServiceClient.Distribution> distributions,
        List<CatalogServiceClient.Vocabulary> vocabularies
    ) {}

    private record Verdict(boolean approved, List<ReviewComment> comments, String summary) {}

    /** One rejected attempt for an element: what was proposed plus the reviewer's issues that iteration. */
    private record ElementAttempt(int iteration, String rejectedClassification, Boolean rejectedPii,
                                  List<ReviewComment> issues) {}
}
