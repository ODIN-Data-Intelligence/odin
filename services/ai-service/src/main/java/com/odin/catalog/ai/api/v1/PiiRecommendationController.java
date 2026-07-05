package com.odin.catalog.ai.api.v1;

import com.odin.catalog.ai.api.v1.dto.PiiRecommendationRequest;
import com.odin.catalog.ai.api.v1.dto.PiiRecommendationResponse;
import com.odin.catalog.ai.application.PiiRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@Tag(name = "PII Indicators", description = "AI-powered PII and direct identifier recommendations")
@RestController
@RequestMapping("/api/v1/recommend-pii")
@RequiredArgsConstructor
public class PiiRecommendationController {

    private static final Logger log = LoggerFactory.getLogger(PiiRecommendationController.class);

    private final PiiRecommendationService piiRecommendationService;

    @Operation(summary = "Recommend PII indicators",
        description = "Uses the LLM and vocabulary context (DPV-PD, schema.org, FIBO) to determine "
            + "whether each data element contains personal information or is a direct identifier. "
            + "Falls back to field name and dataset heuristics when no vocabulary is mapped.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PII indicator recommendations"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping
    public PiiRecommendationResponse recommend(@RequestBody PiiRecommendationRequest request) {
        log.info("action=RECOMMEND_PII elementCount={}", request.elements().size());
        return piiRecommendationService.recommend(request);
    }
}
