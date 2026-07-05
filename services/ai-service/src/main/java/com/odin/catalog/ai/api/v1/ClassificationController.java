package com.odin.catalog.ai.api.v1;

import com.odin.catalog.ai.api.v1.dto.ClassifyElementsRequest;
import com.odin.catalog.ai.api.v1.dto.ClassifyElementsResponse;
import com.odin.catalog.ai.application.ClassificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Classification", description = "AI-powered data element classification")
@RestController
@RequestMapping("/api/v1/classify")
@RequiredArgsConstructor
public class ClassificationController {

    private static final Logger log = LoggerFactory.getLogger(ClassificationController.class);

    private final ClassificationService classificationService;

    @Operation(summary = "Classify data elements",
        description = "Uses the LLM to infer data classification levels (PUBLIC / INTERNAL / CONFIDENTIAL / HIGH_CONFIDENTIAL) "
            + "from element names, logical types, and FIBO/Schema.org vocabulary mappings. "
            + "Returns one classification with a one-sentence reasoning for each input element.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Classification results"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/elements")
    public ClassifyElementsResponse classify(@RequestBody ClassifyElementsRequest request) {
        log.info("action=CLASSIFY elementCount={}", request.elements().size());
        return classificationService.classify(request);
    }
}
