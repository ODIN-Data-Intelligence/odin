package com.odin.catalog.identity.application;

import com.odin.catalog.identity.api.v1.dto.*;
import com.odin.catalog.identity.infrastructure.jpa.entity.BookmarkCollectionEntity;
import com.odin.catalog.identity.infrastructure.jpa.entity.BookmarkEntity;
import com.odin.catalog.identity.infrastructure.jpa.repository.BookmarkCollectionRepository;
import com.odin.catalog.identity.infrastructure.jpa.repository.BookmarkRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private static final Logger log = LoggerFactory.getLogger(BookmarkService.class);

    private final BookmarkRepository           bookmarkRepository;
    private final BookmarkCollectionRepository collectionRepository;

    // ── Collections ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BookmarkCollectionResponse> listCollections() {
        UUID userId   = currentUserId();
        UUID tenantId = tenantId();
        return collectionRepository
            .findByUserIdAndTenantIdOrderByCreatedAtAsc(userId, tenantId)
            .stream().map(this::toCollectionResponse).toList();
    }

    @Transactional
    public BookmarkCollectionResponse createCollection(BookmarkCollectionRequest req) {
        UUID userId   = currentUserId();
        UUID tenantId = tenantId();
        log.info("action=CREATE_COLLECTION userId={} name={}", userId, req.name());

        BookmarkCollectionEntity entity = new BookmarkCollectionEntity();
        entity.setUserId(userId);
        entity.setTenantId(tenantId);
        entity.setName(req.name());
        entity.setDescription(req.description());
        return toCollectionResponse(collectionRepository.save(entity));
    }

    @Transactional
    public BookmarkCollectionResponse updateCollection(UUID id, BookmarkCollectionRequest req) {
        log.info("action=UPDATE_COLLECTION id={}", id);
        BookmarkCollectionEntity entity = findCollectionOrThrow(id);
        entity.setName(req.name());
        entity.setDescription(req.description());
        return toCollectionResponse(collectionRepository.save(entity));
    }

    @Transactional
    public void deleteCollection(UUID id) {
        log.info("action=DELETE_COLLECTION id={}", id);
        BookmarkCollectionEntity entity = findCollectionOrThrow(id);
        // ON DELETE SET NULL handles nulling bookmarks.collection_id in the DB
        collectionRepository.delete(entity);
    }

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BookmarkResponse> listBookmarks(UUID collectionId) {
        UUID userId   = currentUserId();
        UUID tenantId = tenantId();
        List<BookmarkEntity> results = collectionId != null
            ? bookmarkRepository.findByUserIdAndTenantIdAndCollectionIdOrderByCreatedAtDesc(userId, tenantId, collectionId)
            : bookmarkRepository.findByUserIdAndTenantIdOrderByCreatedAtDesc(userId, tenantId);
        return results.stream().map(this::toBookmarkResponse).toList();
    }

    @Transactional
    public BookmarkResponse createBookmark(BookmarkRequest req) {
        UUID userId   = currentUserId();
        UUID tenantId = tenantId();
        log.info("action=CREATE_BOOKMARK userId={} datasetId={}", userId, req.datasetId());

        // Idempotent — return existing bookmark if dataset already bookmarked
        Optional<BookmarkEntity> existing = bookmarkRepository.findByUserIdAndDatasetId(userId, req.datasetId());
        if (existing.isPresent()) return toBookmarkResponse(existing.get());

        if (req.collectionId() != null) {
            findCollectionOrThrow(req.collectionId());
        }

        BookmarkEntity entity = new BookmarkEntity();
        entity.setUserId(userId);
        entity.setTenantId(tenantId);
        entity.setDatasetId(req.datasetId());
        entity.setDatasetTitle(req.datasetTitle());
        entity.setCollectionId(req.collectionId());
        entity.setNote(req.note());
        return toBookmarkResponse(bookmarkRepository.save(entity));
    }

    @Transactional
    public BookmarkResponse patchBookmark(UUID id, BookmarkPatchRequest req) {
        log.info("action=PATCH_BOOKMARK id={}", id);
        BookmarkEntity entity = findBookmarkOrThrow(id);

        if (req.collectionId() != null) {
            findCollectionOrThrow(req.collectionId());
        }
        entity.setCollectionId(req.collectionId());
        entity.setNote(req.note());
        return toBookmarkResponse(bookmarkRepository.save(entity));
    }

    @Transactional
    public void deleteBookmark(UUID id) {
        log.info("action=DELETE_BOOKMARK id={}", id);
        BookmarkEntity entity = findBookmarkOrThrow(id);
        bookmarkRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public BookmarkResponse getByDatasetId(UUID datasetId) {
        UUID userId = currentUserId();
        return bookmarkRepository.findByUserIdAndDatasetId(userId, datasetId)
            .map(this::toBookmarkResponse)
            .orElseThrow(() -> new NoSuchElementException("No bookmark for dataset: " + datasetId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BookmarkCollectionEntity findCollectionOrThrow(UUID id) {
        UUID userId   = currentUserId();
        UUID tenantId = tenantId();
        return collectionRepository.findByIdAndUserIdAndTenantId(id, userId, tenantId)
            .orElseThrow(() -> new NoSuchElementException("Collection not found: " + id));
    }

    private BookmarkEntity findBookmarkOrThrow(UUID id) {
        UUID userId   = currentUserId();
        UUID tenantId = tenantId();
        return bookmarkRepository.findByIdAndUserIdAndTenantId(id, userId, tenantId)
            .orElseThrow(() -> new NoSuchElementException("Bookmark not found: " + id));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new IllegalStateException("No authentication in context");
        if (auth.getPrincipal() instanceof Jwt jwt) return UUID.fromString(jwt.getSubject());
        // Dev API key fallback — use a fixed dev user UUID
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private UUID tenantId() {
        String t = TenantContextHolder.get();
        return UUID.fromString(t != null ? t : "00000000-0000-0000-0000-000000000001");
    }

    private BookmarkCollectionResponse toCollectionResponse(BookmarkCollectionEntity e) {
        return new BookmarkCollectionResponse(
            e.getId(), e.getTenantId(), e.getUserId(),
            e.getName(), e.getDescription(),
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private BookmarkResponse toBookmarkResponse(BookmarkEntity e) {
        return new BookmarkResponse(
            e.getId(), e.getTenantId(), e.getUserId(),
            e.getDatasetId(), e.getDatasetTitle(),
            e.getCollectionId(), e.getNote(),
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
