package com.odin.catalog.ai.api.v1;

import com.odin.catalog.ai.api.v1.dto.SemanticRecommendationRequest;
import com.odin.catalog.ai.api.v1.dto.SemanticRecommendationResponse;
import com.odin.catalog.ai.application.SemanticRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Semantic Recommendation", description = "AI-powered dataset semantic type recommendations")
@RestController
@RequestMapping("/api/v1/recommend-semantic-context")
@RequiredArgsConstructor
public class SemanticRecommendationController {

    private static final Logger log = LoggerFactory.getLogger(SemanticRecommendationController.class);

    private final SemanticRecommendationService recommendationService;

    @Operation(summary = "Recommend semantic types for a dataset",
        description = "Uses the LLM to analyse dataset metadata (title, description, keywords, element names, " +
            "logical types, current vocabulary mappings) and recommends additional business domain types " +
            "and vocabulary concepts to improve semantic coverage.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Semantic type recommendations"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping
    public SemanticRecommendationResponse recommend(@RequestBody SemanticRecommendationRequest request) {
        log.info("action=RECOMMEND_SEMANTIC");
        return recommendationService.recommend(request);
    }
}
