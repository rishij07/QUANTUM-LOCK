package com.quantumlock.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.quantumlock.domain.AuditEvent;
import com.quantumlock.repo.AuditRepository;
import com.quantumlock.security.UserPrincipal;
import com.quantumlock.service.Dto.AuditRow;
import com.quantumlock.service.Dto.ChainStatus;
import com.quantumlock.service.Dto.TamperSimulation;

/**
 * Append-only audit log. Every row stores hash = SHA-256(prev_hash | ts | user | tenant | action | resource | result | detail).
 * Rewriting any past row breaks its own hash and every link after it.
 */
@Service
public class AuditService {
    public static final String GENESIS = "GENESIS";
    private final AuditRepository repo;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate requiresNew;

    public AuditService(AuditRepository repo, JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public AuditEvent record(UserPrincipal u, String action, String resource, String result, String detail) {
        return record(u.email(), u.tenantName(), action, resource, result, detail);
    }

    /** Runs in its own transaction so the event survives even when the caller's transaction rolls back. */
    public AuditEvent record(String user, String tenant, String action, String resource, String result, String detail) {
        return requiresNew.execute(status -> append(user, tenant, action, resource, result, detail));
    }

    private AuditEvent append(String user, String tenant, String action, String resource, String result, String detail) {
        jdbc.execute("select pg_advisory_xact_lock(727001)");   // serialise chain writers
        AuditEvent last = repo.findTopByOrderByIdDesc();
        AuditEvent e = new AuditEvent();
        e.ts = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        e.userEmail = user;
        e.tenantName = tenant;
        e.action = action;
        e.resource = clip(resource, 380);
        e.result = result;
        e.detail = clip(detail, 900);
        e.prevHash = last == null ? GENESIS : last.hash;
        e.hash = hash(e);
        return repo.save(e);
    }

    public static String hash(AuditEvent e) {
        String payload = String.join("|", nz(e.prevHash), String.valueOf(e.ts.toEpochMilli()), nz(e.userEmail), nz(e.tenantName),
                nz(e.action), nz(e.resource), nz(e.result), nz(e.detail));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<AuditRow> list(UserPrincipal u, int limit) {
        var page = PageRequest.of(0, Math.max(1, Math.min(limit, 300)));
        List<AuditEvent> events = u.isAdmin()
                ? repo.findByTenantNameOrderByIdDesc(u.tenantName(), page)
                : repo.findByUserEmailOrderByIdDesc(u.email(), page);
        return events.stream().map(AuditService::row).toList();
    }

    public ChainStatus verify() {
        List<AuditEvent> all = repo.findAllByOrderByIdAsc();
        String prev = GENESIS;
        for (AuditEvent e : all) {
            if (!prev.equals(e.prevHash)) {
                return new ChainStatus(false, all.size(), e.id, "Chain link broken at event #" + e.id + ": prev_hash does not match the previous event.");
            }
            if (!hash(e).equals(e.hash)) {
                return new ChainStatus(false, all.size(), e.id, "Event #" + e.id + " was modified: stored hash does not match its contents.");
            }
            prev = e.hash;
        }
        return new ChainStatus(true, all.size(), null, "All " + all.size() + " events verified. Chain intact.");
    }

    /** Non-destructive demo: forge one event IN MEMORY and show that verification catches it. */
    public TamperSimulation simulateTamper() {
        List<AuditEvent> all = repo.findAllByOrderByIdAsc();
        AuditEvent target = null;
        for (AuditEvent e : all) {
            if (!"SUCCESS".equals(e.result)) target = e;      // last non-success event
        }
        if (target == null && !all.isEmpty()) target = all.get(all.size() - 1);
        if (target == null) {
            return new TamperSimulation(null, "-", "-", "-", "-", "-", false, null, "No audit events yet.");
        }
        AuditEvent forged = target.copy();
        String original = forged.result;
        forged.result = "SUCCESS";                             // attacker rewrites history
        String recomputed = hash(forged);
        boolean detected = !recomputed.equals(target.hash);
        return new TamperSimulation(target.id, target.action, original, forged.result, target.hash, recomputed, detected,
                target.id, "An attacker changes event #" + target.id + " from " + original + " to SUCCESS. Its SHA-256 no longer matches the stored hash, "
                + "and every later event links to the old hash, so the whole chain from #" + target.id + " onward fails verification. "
                + "(Nothing was written to the database.)");
    }

    public long countSince(String tenant, Instant since, java.util.Collection<String> results) {
        return repo.countByTenantNameAndResultInAndTsAfter(tenant, results, since);
    }

    public static AuditRow row(AuditEvent e) {
        return new AuditRow(e.id, e.ts, e.userEmail, e.tenantName, e.action, e.resource, e.result, e.detail, e.hash, e.prevHash);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
