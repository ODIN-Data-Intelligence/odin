package com.odin.catalog.ai.api.v1;

import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationRequest;
import com.odin.catalog.ai.api.v1.dto.VocabConceptRecommendationResponse;
import com.odin.catalog.ai.application.VocabConceptRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vocabulary Concepts", description = "AI-powered vocabulary concept mapping recommendations")
@RestController
@RequiredArgsConstructor
public class VocabConceptRecommendationController {

    private final VocabConceptRecommendationService recommendationService;

    @Operation(summary = "Recommend vocabulary concept mappings",
        description = "Uses the LLM to suggest relevant SKOS concept mappings from available vocabularies "
            + "(FIBO, schema.org, SKOS, Dublin Core, etc.) for each input data element. "
            + "Returns up to 5 concept suggestions per element with match type and reasoning.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vocabulary concept recommendations"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/api/v1/recommend-vocab-concepts")
    public VocabConceptRecommendationResponse recommend(@RequestBody VocabConceptRecommendationRequest request) {
        return recommendationService.recommend(request);
    }
}
