package com.odin.catalog.identity.infrastructure.jpa.repository;

import com.odin.catalog.identity.infrastructure.jpa.entity.BookmarkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookmarkRepository extends JpaRepository<BookmarkEntity, UUID> {

    List<BookmarkEntity> findByUserIdAndTenantIdOrderByCreatedAtDesc(UUID userId, UUID tenantId);

    List<BookmarkEntity> findByUserIdAndTenantIdAndCollectionIdOrderByCreatedAtDesc(UUID userId, UUID tenantId, UUID collectionId);

    Optional<BookmarkEntity> findByUserIdAndDatasetId(UUID userId, UUID datasetId);

    Optional<BookmarkEntity> findByIdAndUserIdAndTenantId(UUID id, UUID userId, UUID tenantId);
}
