package com.odin.catalog.identity.api.v1;

import com.odin.catalog.identity.api.v1.dto.BookmarkPatchRequest;
import com.odin.catalog.identity.api.v1.dto.BookmarkRequest;
import com.odin.catalog.identity.api.v1.dto.BookmarkResponse;
import com.odin.catalog.identity.application.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Bookmarks", description = "Save and organise datasets into personal bookmark lists")
@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarksController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "List bookmarks",
        description = "Returns the current user's bookmarks. Pass collectionId to filter by collection.")
    @ApiResponse(responseCode = "200", description = "Bookmarks listed")
    @GetMapping
    public List<BookmarkResponse> list(
            @Parameter(description = "Filter by collection UUID") @RequestParam(required = false) UUID collectionId) {
        return bookmarkService.listBookmarks(collectionId);
    }

    @Operation(summary = "Bookmark a dataset",
        description = "Adds a dataset to the current user's bookmarks. Idempotent — returns the existing bookmark if already bookmarked.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Dataset bookmarked"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookmarkResponse create(@Valid @RequestBody BookmarkRequest request) {
        return bookmarkService.createBookmark(request);
    }

    @Operation(summary = "Update bookmark",
        description = "Moves a bookmark to a different collection or updates its note. Pass collectionId: null to remove from any collection.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bookmark updated"),
        @ApiResponse(responseCode = "404", description = "Bookmark not found", content = @Content)
    })
    @PatchMapping("/{id}")
    public BookmarkResponse patch(
            @Parameter(description = "Bookmark UUID") @PathVariable UUID id,
            @RequestBody BookmarkPatchRequest request) {
        return bookmarkService.patchBookmark(id, request);
    }

    @Operation(summary = "Remove bookmark", description = "Removes a dataset from the current user's bookmarks.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Bookmark removed"),
        @ApiResponse(responseCode = "404", description = "Bookmark not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Bookmark UUID") @PathVariable UUID id) {
        bookmarkService.deleteBookmark(id);
    }

    @Operation(summary = "Check if dataset is bookmarked",
        description = "Returns the bookmark for a given dataset if it exists, or 404 if the dataset is not bookmarked. Used by the UI toggle button.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dataset is bookmarked"),
        @ApiResponse(responseCode = "404", description = "Dataset is not bookmarked", content = @Content)
    })
    @GetMapping("/dataset/{datasetId}")
    public BookmarkResponse getByDataset(
            @Parameter(description = "Dataset UUID") @PathVariable UUID datasetId) {
        return bookmarkService.getByDatasetId(datasetId);
    }
}
