package com.odin.catalog.ai.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A single Server-Sent Event emitted by the agentic review loop. One JSON object is sent per
 * SSE {@code data:} line. Fields are populated selectively per {@link #phase}:
 * <ul>
 *   <li>CONTEXT / PROPOSING / REVIEWING — progress markers, only {@code phase}/{@code iteration}</li>
 *   <li>PROPOSAL — {@code proposal} holds the proposer's combined output for this iteration</li>
 *   <li>REVIEW — {@code verdict}, {@code comments}, {@code summary} from the reviewer</li>
 *   <li>DONE / MAX_REACHED — {@code proposal} holds the final, persisted proposal</li>
 *   <li>ERROR — {@code message} describes the failure; the stream then completes</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "One progress event in the agentic proposer/reviewer stream")
public record AgenticEvent(

    @Schema(description = "Phase marker",
        allowableValues = {"CONTEXT", "MEMORY", "PROPOSING", "PROPOSAL", "REVIEWING", "REVIEW", "LOCKED", "DONE", "MAX_REACHED", "ERROR"})
    String phase,

    @Schema(description = "1-based loop iteration this event belongs to")
    Integer iteration,

    @Schema(description = "Proposer output (PROPOSAL) or final persisted result (DONE/MAX_REACHED)")
    CombinedProposal proposal,

    @Schema(description = "Reviewer verdict", allowableValues = {"APPROVE", "REJECT"})
    String verdict,

    @Schema(description = "Reviewer per-issue comments (REVIEW)")
    List<ReviewComment> comments,

    @Schema(description = "Reviewer one-line summary (REVIEW)")
    String summary,

    @Schema(description = "Human-readable message for ERROR / informational events")
    String message
) {

    public static AgenticEvent marker(String phase, Integer iteration) {
        return new AgenticEvent(phase, iteration, null, null, null, null, null);
    }

    /** Informational marker carrying a human-readable message (e.g. the MEMORY phase). */
    public static AgenticEvent message(String phase, String message) {
        return new AgenticEvent(phase, 0, null, null, null, null, message);
    }

    public static AgenticEvent proposal(int iteration, CombinedProposal proposal) {
        return new AgenticEvent("PROPOSAL", iteration, proposal, null, null, null, null);
    }

    public static AgenticEvent review(int iteration, String verdict, List<ReviewComment> comments, String summary) {
        return new AgenticEvent("REVIEW", iteration, null, verdict, comments, summary, null);
    }

    public static AgenticEvent terminal(String phase, int iteration, CombinedProposal proposal) {
        return new AgenticEvent(phase, iteration, proposal, null, null, null, null);
    }

    public static AgenticEvent error(int iteration, String message) {
        return new AgenticEvent("ERROR", iteration, null, null, null, null, message);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CombinedProposal(List<ElementProposal> elements) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ElementProposal(
        String elementId,
        String name,
        String description,
        String descriptionReasoning,
        String classification,
        String classificationReasoning,
        List<VocabConceptProposal> vocabConcepts,
        Boolean isPersonalInformation,
        Boolean isDirectIdentifier,
        String piiReasoning
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VocabConceptProposal(
        String conceptIri,
        String conceptLabel,
        String conceptDefinition,
        String matchType,
        String reasoning
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReviewComment(
        String elementId,
        String dimension,
        String issue
    ) {}
}
