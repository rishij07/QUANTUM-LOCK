package com.quantumlock.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.quantumlock.domain.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** Tenant-scoped query: own tenant's documents + documents with an ACTIVE share to this user. */
    @Query("select d from Document d where d.tenantId = :tenantId or d.id in "
            + "(select g.documentId from ShareGrant g where g.granteeId = :userId and g.status = 'ACTIVE') "
            + "order by d.createdAt desc")
    List<Document> findVisible(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    List<Document> findByTenantId(UUID tenantId);

    List<Document> findByTenantIdNot(UUID tenantId);
}
