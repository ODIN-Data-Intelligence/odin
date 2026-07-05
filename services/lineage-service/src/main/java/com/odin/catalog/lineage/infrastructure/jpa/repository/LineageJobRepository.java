package com.odin.catalog.lineage.infrastructure.jpa.repository;

import com.odin.catalog.lineage.infrastructure.jpa.entity.LineageJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LineageJobRepository extends JpaRepository<LineageJobEntity, UUID> {

    Optional<LineageJobEntity> findByNamespaceAndName(String namespace, String name);

    /**
     * Idempotent insert keyed on the existing {@code UNIQUE (namespace, name)} constraint. Returns
     * the number of rows inserted (1 = created, 0 = a concurrent consumer already created it).
     * ON CONFLICT DO NOTHING does not raise, so it never poisons the enclosing transaction.
     */
    @Modifying
    @Query(value = "INSERT INTO lineage_jobs (namespace, name) VALUES (:namespace, :name) "
        + "ON CONFLICT (namespace, name) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("namespace") String namespace, @Param("name") String name);
}
