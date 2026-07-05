package com.odin.catalog.ai.api.v1;

import com.odin.catalog.ai.api.v1.dto.ClassifyElementsRequest;
import com.odin.catalog.ai.api.v1.dto.DescribeElementsResponse;
import com.odin.catalog.ai.application.DescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Description", description = "AI-powered data element description generation")
@RestController
@RequestMapping("/api/v1/describe")
@RequiredArgsConstructor
public class DescriptionController {

    private static final Logger log = LoggerFactory.getLogger(DescriptionController.class);

    private final DescriptionService descriptionService;

    @Operation(summary = "Recommend descriptions for data elements",
        description = "Uses the LLM to generate a business-facing description for each element based on "
            + "its name, label, logical type, and FIBO/schema.org vocabulary concept mappings. "
            + "Returns one suggested description with a one-sentence reasoning for each input element.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Description recommendations"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid auth", content = @Content)
    })
    @PostMapping("/elements")
    public DescribeElementsResponse describe(@RequestBody ClassifyElementsRequest request) {
        log.info("action=DESCRIBE elementCount={}", request.elements().size());
        return descriptionService.describe(request);
    }
}
