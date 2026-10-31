package com.quantumlock.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quantumlock.domain.DocumentVersion;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
    Optional<DocumentVersion> findByDocumentIdAndVersion(UUID documentId, int version);

    List<DocumentVersion> findByDocumentIdOrderByVersionDesc(UUID documentId);

    boolean existsByTenantIdAndContentIndex(UUID tenantId, String contentIndex);
}
