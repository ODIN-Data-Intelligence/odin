package com.odin.catalog.identity.infrastructure.jpa.repository;

import com.odin.catalog.identity.infrastructure.jpa.entity.BookmarkCollectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookmarkCollectionRepository extends JpaRepository<BookmarkCollectionEntity, UUID> {

    List<BookmarkCollectionEntity> findByUserIdAndTenantIdOrderByCreatedAtAsc(UUID userId, UUID tenantId);

    Optional<BookmarkCollectionEntity> findByIdAndUserIdAndTenantId(UUID id, UUID userId, UUID tenantId);
}
