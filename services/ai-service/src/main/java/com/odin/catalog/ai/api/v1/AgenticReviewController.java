package com.odin.catalog.ai.api.v1;

import com.odin.catalog.ai.api.v1.dto.AgenticReviewRequest;
import com.odin.catalog.ai.application.AgenticReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "Agentic Review", description = "Run a proposer/reviewer agent loop over a logical model and stream "
    + "progress (proposals and reviewer comments) via Server-Sent Events. The converged recommendation is "
    + "persisted to the model's elements for the data owner to accept or reject.")
@RestController
@RequestMapping("/api/v1/agentic-review")
@RequiredArgsConstructor
public class AgenticReviewController {

    private static final Logger log = LoggerFactory.getLogger(AgenticReviewController.class);

    private final AgenticReviewService agenticReviewService;

    @Operation(summary = "Run the agentic proposer/reviewer review and stream progress",
        description = "Streams the agent loop as Server-Sent Events. Each `data:` line is a JSON AgenticEvent: "
            + "phase markers (CONTEXT/PROPOSING/REVIEWING), the proposer's PROPOSAL, the reviewer's REVIEW "
            + "(verdict + comments), and a terminal DONE / MAX_REACHED / ERROR. The loop is capped at 10 iterations. "
            + "**Note**: Swagger UI does not render SSE — use curl -N or the producer UI.")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "SSE stream of AgenticEvent JSON objects",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                schema = @Schema(implementation = com.odin.catalog.ai.api.v1.dto.AgenticEvent.class))),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> review(
            @Valid @RequestBody AgenticReviewRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        log.info("action=AGENTIC_REVIEW_REQUEST datasetId={} modelId={}", request.datasetId(), request.modelId());
        // Capture the caller's bearer token on the request thread so the loop (running on a virtual
        // thread, off the SecurityContext) can forward it to inventory-service for tenant/user scoping.
        return agenticReviewService.review(request.datasetId(), request.modelId(), authorization);
    }
}
