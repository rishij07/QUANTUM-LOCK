package com.quantumlock.repo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quantumlock.domain.ShareGrant;

public interface ShareGrantRepository extends JpaRepository<ShareGrant, UUID> {
    List<ShareGrant> findByGranteeId(UUID granteeId);

    List<ShareGrant> findByDocumentIdAndGranteeId(UUID documentId, UUID granteeId);

    List<ShareGrant> findByDocumentIdIn(Collection<UUID> documentIds);
}
