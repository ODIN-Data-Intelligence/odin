package com.odin.catalog.identity.application;

import com.odin.catalog.identity.api.v1.dto.BookmarkCollectionRequest;
import com.odin.catalog.identity.api.v1.dto.BookmarkCollectionResponse;
import com.odin.catalog.identity.api.v1.dto.BookmarkPatchRequest;
import com.odin.catalog.identity.api.v1.dto.BookmarkRequest;
import com.odin.catalog.identity.api.v1.dto.BookmarkResponse;
import com.odin.catalog.identity.infrastructure.jpa.entity.BookmarkCollectionEntity;
import com.odin.catalog.identity.infrastructure.jpa.entity.BookmarkEntity;
import com.odin.catalog.identity.infrastructure.jpa.repository.BookmarkCollectionRepository;
import com.odin.catalog.identity.infrastructure.jpa.repository.BookmarkRepository;
import com.odin.catalog.shared.auth.filter.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    // Dev API key fallback UUID — from BookmarkService.currentUserId()
    static final UUID USER   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock BookmarkRepository           bookmarkRepository;
    @Mock BookmarkCollectionRepository collectionRepository;

    @InjectMocks BookmarkService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TENANT.toString());
        // Non-Jwt principal → service falls back to fixed dev UUID
        Authentication auth = new UsernamePasswordAuthenticationToken("dev-api-key", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ── listCollections ───────────────────────────────────────────────────

    @Test
    void listCollections_returnsUserCollections() {
        BookmarkCollectionEntity col = collection("Favourites");
        when(collectionRepository.findByUserIdAndTenantIdOrderByCreatedAtAsc(USER, TENANT))
            .thenReturn(List.of(col));

        List<BookmarkCollectionResponse> result = service.listCollections();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Favourites");
    }

    // ── createCollection ──────────────────────────────────────────────────

    @Test
    void createCollection_savesEntityWithCorrectFields() {
        BookmarkCollectionRequest req = new BookmarkCollectionRequest("Watchlist", "Datasets to review");
        BookmarkCollectionEntity saved = collection("Watchlist");
        when(collectionRepository.save(any())).thenReturn(saved);

        BookmarkCollectionResponse result = service.createCollection(req);

        ArgumentCaptor<BookmarkCollectionEntity> captor = ArgumentCaptor.forClass(BookmarkCollectionEntity.class);
        verify(collectionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().getName()).isEqualTo("Watchlist");
        assertThat(result.name()).isEqualTo("Watchlist");
    }

    // ── updateCollection ──────────────────────────────────────────────────

    @Test
    void updateCollection_found_appliesChanges() {
        BookmarkCollectionEntity col = collection("Old Name");
        when(collectionRepository.findByIdAndUserIdAndTenantId(col.getId(), USER, TENANT))
            .thenReturn(Optional.of(col));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookmarkCollectionResponse result = service.updateCollection(col.getId(),
            new BookmarkCollectionRequest("New Name", "Updated desc"));

        assertThat(result.name()).isEqualTo("New Name");
        assertThat(col.getDescription()).isEqualTo("Updated desc");
    }

    @Test
    void updateCollection_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(collectionRepository.findByIdAndUserIdAndTenantId(id, USER, TENANT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCollection(id, new BookmarkCollectionRequest("n", null)))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── deleteCollection ──────────────────────────────────────────────────

    @Test
    void deleteCollection_found_deletesEntity() {
        BookmarkCollectionEntity col = collection("To Delete");
        when(collectionRepository.findByIdAndUserIdAndTenantId(col.getId(), USER, TENANT))
            .thenReturn(Optional.of(col));

        service.deleteCollection(col.getId());

        verify(collectionRepository).delete(col);
    }

    @Test
    void deleteCollection_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(collectionRepository.findByIdAndUserIdAndTenantId(id, USER, TENANT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCollection(id))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── listBookmarks ─────────────────────────────────────────────────────

    @Test
    void listBookmarks_noCollectionId_returnsAllForUser() {
        when(bookmarkRepository.findByUserIdAndTenantIdOrderByCreatedAtDesc(USER, TENANT))
            .thenReturn(List.of(bookmark(UUID.randomUUID(), null)));

        List<BookmarkResponse> result = service.listBookmarks(null);

        assertThat(result).hasSize(1);
        verify(bookmarkRepository).findByUserIdAndTenantIdOrderByCreatedAtDesc(USER, TENANT);
        verify(bookmarkRepository, never())
            .findByUserIdAndTenantIdAndCollectionIdOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void listBookmarks_withCollectionId_filtersById() {
        UUID colId = UUID.randomUUID();
        when(bookmarkRepository.findByUserIdAndTenantIdAndCollectionIdOrderByCreatedAtDesc(USER, TENANT, colId))
            .thenReturn(List.of(bookmark(UUID.randomUUID(), colId)));

        List<BookmarkResponse> result = service.listBookmarks(colId);

        assertThat(result).hasSize(1);
        verify(bookmarkRepository).findByUserIdAndTenantIdAndCollectionIdOrderByCreatedAtDesc(USER, TENANT, colId);
    }

    // ── createBookmark ────────────────────────────────────────────────────

    @Test
    void createBookmark_alreadyBookmarked_returnsExisting() {
        UUID datasetId = UUID.randomUUID();
        BookmarkEntity existing = bookmark(datasetId, null);
        when(bookmarkRepository.findByUserIdAndDatasetId(USER, datasetId)).thenReturn(Optional.of(existing));

        BookmarkResponse result = service.createBookmark(
            new BookmarkRequest(datasetId, "My Dataset", null, null));

        assertThat(result.datasetId()).isEqualTo(datasetId);
        verify(bookmarkRepository, never()).save(any());
    }

    @Test
    void createBookmark_newBookmark_savesWithCorrectFields() {
        UUID datasetId = UUID.randomUUID();
        when(bookmarkRepository.findByUserIdAndDatasetId(USER, datasetId)).thenReturn(Optional.empty());

        BookmarkEntity saved = bookmark(datasetId, null);
        saved.setNote("Interesting dataset");
        when(bookmarkRepository.save(any())).thenReturn(saved);

        BookmarkResponse result = service.createBookmark(
            new BookmarkRequest(datasetId, "My Dataset", null, "Interesting dataset"));

        ArgumentCaptor<BookmarkEntity> captor = ArgumentCaptor.forClass(BookmarkEntity.class);
        verify(bookmarkRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().getDatasetId()).isEqualTo(datasetId);
        assertThat(captor.getValue().getNote()).isEqualTo("Interesting dataset");
    }

    @Test
    void createBookmark_withValidCollectionId_verifiesCollectionExists() {
        UUID datasetId   = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        when(bookmarkRepository.findByUserIdAndDatasetId(USER, datasetId)).thenReturn(Optional.empty());
        when(collectionRepository.findByIdAndUserIdAndTenantId(collectionId, USER, TENANT))
            .thenReturn(Optional.of(collection("My List")));
        when(bookmarkRepository.save(any())).thenReturn(bookmark(datasetId, collectionId));

        service.createBookmark(new BookmarkRequest(datasetId, "DS", collectionId, null));

        verify(collectionRepository).findByIdAndUserIdAndTenantId(collectionId, USER, TENANT);
    }

    @Test
    void createBookmark_withInvalidCollectionId_throwsNoSuchElement() {
        UUID datasetId   = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        when(bookmarkRepository.findByUserIdAndDatasetId(USER, datasetId)).thenReturn(Optional.empty());
        when(collectionRepository.findByIdAndUserIdAndTenantId(collectionId, USER, TENANT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBookmark(
            new BookmarkRequest(datasetId, "DS", collectionId, null)))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── patchBookmark ─────────────────────────────────────────────────────

    @Test
    void patchBookmark_found_updatesCollectionAndNote() {
        BookmarkEntity bm = bookmark(UUID.randomUUID(), null);
        when(bookmarkRepository.findByIdAndUserIdAndTenantId(bm.getId(), USER, TENANT))
            .thenReturn(Optional.of(bm));
        when(bookmarkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID newColId = UUID.randomUUID();
        when(collectionRepository.findByIdAndUserIdAndTenantId(newColId, USER, TENANT))
            .thenReturn(Optional.of(collection("New List")));

        service.patchBookmark(bm.getId(), new BookmarkPatchRequest(newColId, "updated note"));

        assertThat(bm.getCollectionId()).isEqualTo(newColId);
        assertThat(bm.getNote()).isEqualTo("updated note");
    }

    @Test
    void patchBookmark_nullCollectionId_onlyUpdatesNote() {
        BookmarkEntity bm = bookmark(UUID.randomUUID(), null);
        when(bookmarkRepository.findByIdAndUserIdAndTenantId(bm.getId(), USER, TENANT))
            .thenReturn(Optional.of(bm));
        when(bookmarkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.patchBookmark(bm.getId(), new BookmarkPatchRequest(null, "updated note"));

        assertThat(bm.getNote()).isEqualTo("updated note");
        assertThat(bm.getCollectionId()).isNull();
        verify(collectionRepository, never()).findByIdAndUserIdAndTenantId(any(), any(), any());
    }

    @Test
    void patchBookmark_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(bookmarkRepository.findByIdAndUserIdAndTenantId(id, USER, TENANT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patchBookmark(id, new BookmarkPatchRequest(null, null)))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── deleteBookmark ────────────────────────────────────────────────────

    @Test
    void deleteBookmark_found_deletesEntity() {
        BookmarkEntity bm = bookmark(UUID.randomUUID(), null);
        when(bookmarkRepository.findByIdAndUserIdAndTenantId(bm.getId(), USER, TENANT))
            .thenReturn(Optional.of(bm));

        service.deleteBookmark(bm.getId());

        verify(bookmarkRepository).delete(bm);
    }

    @Test
    void deleteBookmark_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(bookmarkRepository.findByIdAndUserIdAndTenantId(id, USER, TENANT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteBookmark(id))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── getByDatasetId ────────────────────────────────────────────────────

    @Test
    void getByDatasetId_found_returnsResponse() {
        UUID datasetId = UUID.randomUUID();
        BookmarkEntity bm = bookmark(datasetId, null);
        when(bookmarkRepository.findByUserIdAndDatasetId(USER, datasetId)).thenReturn(Optional.of(bm));

        BookmarkResponse result = service.getByDatasetId(datasetId);

        assertThat(result.datasetId()).isEqualTo(datasetId);
    }

    @Test
    void getByDatasetId_notFound_throwsNoSuchElement() {
        UUID datasetId = UUID.randomUUID();
        when(bookmarkRepository.findByUserIdAndDatasetId(USER, datasetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByDatasetId(datasetId))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── currentUserId ─────────────────────────────────────────────────────

    @Test
    void listCollections_jwtPrincipal_extractsUserIdFromSubject() {
        UUID jwtUserId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claim("sub", jwtUserId.toString())
            .build();
        Authentication auth = new UsernamePasswordAuthenticationToken(jwt, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(collectionRepository.findByUserIdAndTenantIdOrderByCreatedAtAsc(jwtUserId, TENANT))
            .thenReturn(List.of());

        service.listCollections();

        verify(collectionRepository).findByUserIdAndTenantIdOrderByCreatedAtAsc(eq(jwtUserId), any());
    }

    @Test
    void listCollections_nullAuthentication_throwsIllegalState() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.listCollections())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No authentication");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    static BookmarkCollectionEntity collection(String name) {
        BookmarkCollectionEntity e = new BookmarkCollectionEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(USER);
        e.setTenantId(TENANT);
        e.setName(name);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    static BookmarkEntity bookmark(UUID datasetId, UUID collectionId) {
        BookmarkEntity e = new BookmarkEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(USER);
        e.setTenantId(TENANT);
        e.setDatasetId(datasetId);
        e.setDatasetTitle("Test Dataset");
        e.setCollectionId(collectionId);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }
}
