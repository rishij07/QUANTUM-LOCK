package com.quantumlock.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.quantumlock.config.ApiException;
import com.quantumlock.domain.AppUser;
import com.quantumlock.domain.Document;
import com.quantumlock.domain.ShareGrant;
import com.quantumlock.domain.Tenant;
import com.quantumlock.repo.DocumentRepository;
import com.quantumlock.repo.ShareGrantRepository;
import com.quantumlock.repo.TenantRepository;
import com.quantumlock.repo.UserRepository;
import com.quantumlock.security.UserPrincipal;
import com.quantumlock.service.AccessPolicy.Action;
import com.quantumlock.service.Dto.ShareRow;

@Service
public class ShareService {
    private static final Set<String> PERMISSIONS = Set.of("VIEW", "DOWNLOAD");

    private final ShareGrantRepository grants;
    private final DocumentRepository docs;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final DocumentService documents;
    private final AuditService audit;

    public ShareService(ShareGrantRepository grants, DocumentRepository docs, UserRepository users, TenantRepository tenants,
                        DocumentService documents, AuditService audit) {
        this.grants = grants;
        this.docs = docs;
        this.users = users;
        this.tenants = tenants;
        this.documents = documents;
        this.audit = audit;
    }

    @Transactional
    public ShareRow share(UserPrincipal u, UUID docId, String email, String permission) {
        Document d = documents.find(docId);
        documents.authorize(u, d, Action.SHARE, "SHARE", null);      // only owner / tenant admin
        String perm = permission == null ? "DOWNLOAD" : permission.toUpperCase();
        if (!PERMISSIONS.contains(perm)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PERMISSION", "Permission must be VIEW or DOWNLOAD");
        }
        AppUser grantee = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(x -> x.enabled)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "No such user"));
        if (grantee.id.equals(u.id()) || grantee.id.equals(d.ownerId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_GRANTEE", "Cannot share a document with its owner or with yourself");
        }
        List<ShareGrant> existing = grants.findByDocumentIdAndGranteeId(d.id, grantee.id);
        ShareGrant g = existing.stream().filter(x -> "ACTIVE".equals(x.status)).findFirst().orElse(null);
        if (g == null) {
            g = new ShareGrant();
            g.id = UUID.randomUUID();
            g.documentId = d.id;
            g.granteeId = grantee.id;
            g.grantedBy = u.id();
            g.status = "ACTIVE";
            g.createdAt = Instant.now();
        }
        g.permission = perm;
        grants.save(g);
        audit.record(u, "SHARE", d.name + " [" + d.id.toString().substring(0, 8) + "]", "SUCCESS",
                "Shared with " + grantee.email + " (" + perm + ")");
        return rows(List.of(g)).get(0);
    }

    @Transactional
    public ShareRow revoke(UserPrincipal u, UUID grantId) {
        ShareGrant g = grants.findById(grantId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Share not found"));
        Document d = documents.find(g.documentId);
        documents.authorize(u, d, Action.SHARE, "REVOKE", null);
        if ("ACTIVE".equals(g.status)) {
            g.status = "REVOKED";
            g.revokedAt = Instant.now();
            g.revokedBy = u.id();
            grants.save(g);
            AppUser grantee = users.findById(g.granteeId).orElse(null);
            audit.record(u, "REVOKE", d.name + " [" + d.id.toString().substring(0, 8) + "]", "SUCCESS",
                    "Revoked access for " + (grantee == null ? "?" : grantee.email));
        }
        return rows(List.of(g)).get(0);
    }

    /** Shares on documents this user manages (owner, or tenant admin). Always limited to the caller's own tenant. */
    public List<ShareRow> outgoing(UserPrincipal u) {
        List<Document> mine = docs.findByTenantId(u.tenantId()).stream()
                .filter(d -> u.isAdmin() || d.ownerId.equals(u.id())).toList();
        if (mine.isEmpty()) return List.of();
        return rows(grants.findByDocumentIdIn(mine.stream().map(d -> d.id).toList()));
    }

    public List<ShareRow> incoming(UserPrincipal u) {
        return rows(grants.findByGranteeId(u.id()));
    }

    private List<ShareRow> rows(List<ShareGrant> gs) {
        if (gs.isEmpty()) return List.of();
        Map<UUID, Document> dm = new HashMap<>();
        docs.findAllById(gs.stream().map(g -> g.documentId).collect(Collectors.toSet())).forEach(d -> dm.put(d.id, d));
        Set<UUID> userIds = new java.util.HashSet<>();
        gs.forEach(g -> { userIds.add(g.granteeId); userIds.add(g.grantedBy); });
        Map<UUID, AppUser> um = new HashMap<>();
        users.findAllById(userIds).forEach(x -> um.put(x.id, x));
        Map<UUID, Tenant> tm = new HashMap<>();
        tenants.findAll().forEach(t -> tm.put(t.id, t));

        List<ShareRow> out = new ArrayList<>();
        for (ShareGrant g : gs) {
            Document d = dm.get(g.documentId);
            AppUser to = um.get(g.granteeId);
            AppUser by = um.get(g.grantedBy);
            Tenant tt = to == null ? null : tm.get(to.tenantId);
            out.add(new ShareRow(g.id, g.documentId, d == null ? "?" : d.name, to == null ? "?" : to.email,
                    tt == null ? "?" : tt.name, by == null ? "?" : by.email, g.permission, g.status, g.createdAt, g.revokedAt));
        }
        out.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return out;
    }
}
