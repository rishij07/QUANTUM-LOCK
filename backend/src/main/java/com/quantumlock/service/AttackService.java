package com.quantumlock.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.quantumlock.config.ApiException;
import com.quantumlock.config.QlockProperties;
import com.quantumlock.domain.AppUser;
import com.quantumlock.domain.Document;
import com.quantumlock.domain.Tenant;
import com.quantumlock.repo.DocumentRepository;
import com.quantumlock.repo.ShareGrantRepository;
import com.quantumlock.repo.TenantRepository;
import com.quantumlock.repo.UserRepository;
import com.quantumlock.security.JwtService;
import com.quantumlock.security.UserPrincipal;
import com.quantumlock.service.Dto.AttackResult;
import com.quantumlock.service.Dto.ShareRow;
import com.quantumlock.service.Dto.TamperSimulation;

import io.jsonwebtoken.JwtException;

/** Runs real attacks against the real code paths and reports what the platform did. */
@Service
public class AttackService {
    public static final String SYSTEM_SLUG = "system";
    public static final String BOT_EMAIL = "vault-bot@quantumlock.internal";
    public static final String CANARY_NAME = "attack-canary.txt";

    private final DocumentService documents;
    private final ShareService shares;
    private final AuditService audit;
    private final JwtService jwt;
    private final DocumentRepository docs;
    private final ShareGrantRepository grants;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final QlockProperties props;

    public AttackService(DocumentService documents, ShareService shares, AuditService audit, JwtService jwt, DocumentRepository docs,
                         ShareGrantRepository grants, TenantRepository tenants, UserRepository users, QlockProperties props) {
        this.documents = documents;
        this.shares = shares;
        this.audit = audit;
        this.jwt = jwt;
        this.docs = docs;
        this.grants = grants;
        this.tenants = tenants;
        this.users = users;
        this.props = props;
    }

    public AttackResult run(UserPrincipal u, String type) {
        return switch (type) {
            case "cross-tenant" -> crossTenant(u);
            case "tampering" -> tampering(u);
            case "revoked" -> revoked(u);
            case "invalid-jwt" -> invalidJwt(u);
            case "duplicate" -> duplicate(u);
            case "audit-tamper" -> auditTamper();
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_ATTACK", "Unknown attack");
        };
    }

    // ------------------------------------------------------------------ attacks

    private AttackResult crossTenant(UserPrincipal u) {
        String title = "Cross-Tenant Access";
        Tenant sys = tenants.findBySlug(SYSTEM_SLUG).orElse(null);
        Document target = null;
        for (Document d : docs.findByTenantIdNot(u.tenantId())) {
            if (sys != null && d.tenantId.equals(sys.id)) continue;
            boolean shared = grants.findByDocumentIdAndGranteeId(d.id, u.id()).stream().anyMatch(g -> "ACTIVE".equals(g.status));
            if (!shared) { target = d; break; }
        }
        if (target == null) {
            return new AttackResult("cross-tenant", title, "INCONCLUSIVE", 0, "No foreign-tenant document available to attack.",
                    List.of(), "Seed data is missing.", null);
        }
        List<String> steps = new ArrayList<>();
        steps.add(u.email() + " (" + u.tenantName() + ") requests GET /api/documents/" + target.id + "/download");
        steps.add("The document belongs to another tenant and was never shared with this user");
        try {
            documents.download(u, target.id, null, "[SIM] cross-tenant probe");
            steps.add("Server returned plaintext \u2014 ISOLATION FAILURE");
            return new AttackResult("cross-tenant", title, "NOT BLOCKED", 200, "The request succeeded. Isolation is broken.", steps,
                    "This should never happen.", null);
        } catch (ApiException e) {
            steps.add("Server responded " + e.status.value() + " \u2014 " + e.getMessage());
            steps.add("Logged as CROSS_TENANT_ATTEMPT in the audit chain");
            return new AttackResult("cross-tenant", title, "BLOCKED", e.status.value(),
                    "Tenant A cannot read Tenant B's document, even with the exact document ID.", steps,
                    "Tenant identity is taken from the authenticated user record on the server. The policy compares it to the document's tenant "
                            + "and denies by default. Object storage is never touched, and no key is unwrapped.", null);
        }
    }

    private AttackResult tampering(UserPrincipal u) {
        String title = "Ciphertext Tampering";
        Document d = documents.pickOwnTenantDownloadable(u).orElse(null);
        if (d == null) {
            return new AttackResult("tampering", title, "INCONCLUSIVE", 0, "No readable document in your tenant.", List.of(), "", null);
        }
        var out = documents.simulateCiphertextTamper(u, d);
        List<String> steps = new ArrayList<>();
        steps.add("Attacker with storage access fetches the encrypted object for \"" + out.documentName() + "\"");
        steps.add("One byte of ciphertext (offset " + out.flippedByteOffset() + ") is modified in a private copy");
        steps.add("Server unwraps the DEK via the KMS and attempts AES-256-GCM decryption");
        steps.add(out.detected() ? "GCM authentication tag mismatch \u2014 TAMPERING DETECTED, no plaintext produced"
                : "Decryption succeeded on modified data \u2014 INTEGRITY FAILURE");
        return new AttackResult("tampering", title, out.detected() ? "DETECTED" : "NOT BLOCKED", out.detected() ? 422 : 200,
                out.detected() ? "TAMPERING DETECTED \u2014 AUTHENTICATION FAILED" : "Modified ciphertext was accepted.", steps,
                "AES-256-GCM is authenticated encryption: any change to the ciphertext (or its IV / associated data) makes the tag check fail, "
                        + "so corrupted plaintext is never returned. The real stored object was not modified by this simulation.", null);
    }

    private AttackResult revoked(UserPrincipal u) {
        String title = "Revoked User Access";
        Tenant sys = tenants.findBySlug(SYSTEM_SLUG).orElse(null);
        AppUser bot = users.findByEmailIgnoreCase(BOT_EMAIL).orElse(null);
        Document canary = sys == null ? null : docs.findByTenantId(sys.id).stream().filter(d -> CANARY_NAME.equals(d.name)).findFirst().orElse(null);
        if (sys == null || bot == null || canary == null) {
            return new AttackResult("revoked", title, "INCONCLUSIVE", 0, "Canary document is missing.", List.of(), "", null);
        }
        UserPrincipal owner = UserPrincipal.of(bot, sys);
        List<String> steps = new ArrayList<>();
        ShareRow grant = shares.share(owner, canary.id, u.email(), "DOWNLOAD");
        steps.add("Owner shares \"" + CANARY_NAME + "\" with " + u.email() + " (DOWNLOAD)");
        try {
            documents.download(u, canary.id, null, "[SIM] while shared");
            steps.add(u.email() + " downloads it \u2014 200 OK (access was legitimately granted)");
        } catch (ApiException e) {
            steps.add("Unexpected failure while shared: " + e.getMessage());
        }
        shares.revoke(owner, grant.id());
        steps.add("Owner revokes the share");
        try {
            documents.download(u, canary.id, null, "[SIM] after revoke");
            steps.add("Download succeeded after revocation \u2014 REVOCATION FAILURE");
            return new AttackResult("revoked", title, "NOT BLOCKED", 200, "Revoked user still had access.", steps, "", null);
        } catch (ApiException e) {
            steps.add(u.email() + " retries the same request \u2014 " + e.status.value() + " " + e.getMessage());
            return new AttackResult("revoked", title, "DENIED", e.status.value(), "403 \u2014 Access Denied", steps,
                    "Revocation is enforced in the backend policy on every request: a REVOKED grant is an explicit deny that overrides all other rules. "
                            + "The frontend is not involved in the decision.", null);
        }
    }

    private AttackResult invalidJwt(UserPrincipal u) {
        String title = "Invalid / Expired JWT";
        String valid = jwt.issue(u);
        String expired = jwt.issueExpired(u);
        String forged = jwt.forgeSignature(valid);
        String none = jwt.unsigned(u);
        List<String> steps = new ArrayList<>();
        int rejected = 0;
        String[][] probes = { {"Expired token (correct signature, past exp)", expired}, {"Forged signature (1 character changed)", forged},
                {"Unsigned token (alg=none downgrade)", none} };
        for (String[] p : probes) {
            try {
                jwt.parse(p[1]);
                steps.add(p[0] + " \u2014 ACCEPTED");
            } catch (JwtException | IllegalArgumentException e) {
                rejected++;
                steps.add(p[0] + " \u2014 rejected (" + e.getClass().getSimpleName() + ")");
            }
        }
        audit.record(u, "ACCESS_DENIED", "JWT validation", rejected == probes.length ? "DENIED" : "SUCCESS",
                "[SIM] " + rejected + "/" + probes.length + " forged or expired tokens rejected");
        boolean ok = rejected == probes.length;
        return new AttackResult("invalid-jwt", title, ok ? "DENIED" : "NOT BLOCKED", ok ? 401 : 200,
                ok ? "401 \u2014 every forged or expired token was refused" : "A bad token was accepted.", steps,
                "Tokens are signed with HMAC-SHA; the signature and expiry are verified before any request reaches business logic. "
                        + "Unsigned (alg=none) tokens are refused, and the user is re-loaded from the database on every request.", forged);
    }

    private AttackResult duplicate(UserPrincipal u) {
        String title = "Duplicate Upload";
        Document d = documents.pickOwnTenantDownloadable(u).orElse(null);
        if (d == null) {
            return new AttackResult("duplicate", title, "INCONCLUSIVE", 0, "No readable document in your tenant.", List.of(), "", null);
        }
        var dl = documents.download(u, d.id, null, "[SIM] duplicate probe");
        List<String> steps = new ArrayList<>();
        steps.add("Attacker re-submits the exact bytes of \"" + d.name + "\" as a new upload");
        try {
            documents.upload(u, "replay-" + d.name, d.contentType, d.classification, dl.plaintext());
            steps.add("Upload accepted \u2014 duplicate not detected");
            return new AttackResult("duplicate", title, "NOT BLOCKED", 200, "Duplicate content was stored again.", steps, "", null);
        } catch (ApiException e) {
            steps.add("Keyed content hash matched an existing version \u2014 " + e.status.value() + " " + e.getMessage());
            return new AttackResult("duplicate", title, "DETECTED", e.status.value(), "DUPLICATE DETECTED \u2014 replay rejected", steps,
                    "Each upload gets a keyed HMAC of its plaintext (key derived from the KEK), unique per tenant. Identical content is rejected "
                            + "without exposing a plain SHA-256 that could be used to confirm file guesses.", null);
        }
    }

    private AttackResult auditTamper() {
        TamperSimulation s = audit.simulateTamper();
        List<String> steps = new ArrayList<>();
        steps.add("Attacker targets audit event #" + s.targetId() + " (" + s.action() + ", result " + s.originalResult() + ")");
        steps.add("Rewrites its result to " + s.forgedResult() + " to hide the evidence");
        steps.add("Stored hash   " + abbreviate(s.storedHash()));
        steps.add("Recomputed    " + abbreviate(s.recomputedHash()));
        steps.add(s.detected() ? "Hashes differ \u2014 chain verification fails at event #" + s.brokenAtId() : "Hashes match \u2014 tampering undetected");
        return new AttackResult("audit-tamper", "Audit Log Tampering", s.detected() ? "DETECTED" : "NOT BLOCKED", s.detected() ? 409 : 200,
                s.detected() ? "DETECTED \u2014 audit chain no longer verifies" : "Tampering went unnoticed.", steps, s.explanation(), null);
    }

    private static String abbreviate(String h) {
        return h == null || h.length() < 20 ? String.valueOf(h) : h.substring(0, 16) + "\u2026" + h.substring(h.length() - 8);
    }
}
