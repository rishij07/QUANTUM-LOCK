package com.quantumlock.repo;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.quantumlock.domain.AuditEvent;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    AuditEvent findTopByOrderByIdDesc();

    List<AuditEvent> findAllByOrderByIdAsc();

    List<AuditEvent> findByTenantNameOrderByIdDesc(String tenantName, Pageable page);

    List<AuditEvent> findByUserEmailOrderByIdDesc(String userEmail, Pageable page);

    long countByTenantNameAndResultInAndTsAfter(String tenantName, Collection<String> results, Instant after);
}
