package com.odin.catalog.identity.api.v1;

import com.odin.catalog.identity.api.v1.dto.BookmarkCollectionRequest;
import com.odin.catalog.identity.api.v1.dto.BookmarkCollectionResponse;
import com.odin.catalog.identity.application.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Bookmark Collections", description = "Named groups for organising bookmarked datasets")
@RestController
@RequestMapping("/api/v1/bookmark-collections")
@RequiredArgsConstructor
public class BookmarkCollectionsController {

    private static final Logger log = LoggerFactory.getLogger(BookmarkCollectionsController.class);

    private final BookmarkService bookmarkService;

    @Operation(summary = "List collections", description = "Returns all bookmark collections owned by the current user.")
    @ApiResponse(responseCode = "200", description = "Collections listed")
    @GetMapping
    public List<BookmarkCollectionResponse> list() {
        return bookmarkService.listCollections();
    }

    @Operation(summary = "Create collection", description = "Creates a new bookmark collection for the current user.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Collection created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookmarkCollectionResponse create(@Valid @RequestBody BookmarkCollectionRequest request) {
        log.info("action=CREATE_COLLECTION name={}", request.name());
        return bookmarkService.createCollection(request);
    }

    @Operation(summary = "Update collection", description = "Renames or updates the description of a bookmark collection.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collection updated"),
        @ApiResponse(responseCode = "404", description = "Collection not found", content = @Content)
    })
    @PutMapping("/{id}")
    public BookmarkCollectionResponse update(
            @Parameter(description = "Collection UUID") @PathVariable UUID id,
            @Valid @RequestBody BookmarkCollectionRequest request) {
        log.info("action=UPDATE_COLLECTION id={}", id);
        return bookmarkService.updateCollection(id, request);
    }

    @Operation(summary = "Delete collection",
        description = "Deletes the collection. Bookmarks in this collection are not deleted — they become uncollected.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Collection deleted"),
        @ApiResponse(responseCode = "404", description = "Collection not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Collection UUID") @PathVariable UUID id) {
        log.info("action=DELETE_COLLECTION id={}", id);
        bookmarkService.deleteCollection(id);
    }
}
