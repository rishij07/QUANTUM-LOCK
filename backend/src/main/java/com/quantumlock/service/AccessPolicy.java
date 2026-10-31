package com.quantumlock.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import com.quantumlock.domain.Document;
import com.quantumlock.domain.ShareGrant;
import com.quantumlock.security.UserPrincipal;

/**
 * The single place where authorization is decided. Pure function of (identity, document, action, grants).
 * Authentication proves who you are; this decides what you may do. Default is DENY.
 */
@Component
public class AccessPolicy {
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String CROSS_TENANT = "CROSS_TENANT";

    public enum Action { VIEW, DOWNLOAD, SHARE }

    public record Decision(boolean allowed, String basis, String code, String message, String detail) {
        static Decision allow(String basis) {
            return new Decision(true, basis, null, null, null);
        }

        static Decision deny(String basis, String code, String message, String detail) {
            return new Decision(false, basis, code, message, detail);
        }
    }

    private static Decision crossTenant() {
        return Decision.deny("CROSS_TENANT", CROSS_TENANT, "Cross-tenant access blocked",
                "The document belongs to another tenant and has not been shared with you.");
    }

    public Decision evaluate(UserPrincipal u, Document d, Action action, Collection<ShareGrant> grants) {
        boolean sameTenant = d.tenantId.equals(u.tenantId());
        boolean owner = d.ownerId.equals(u.id());
        boolean admin = sameTenant && u.isAdmin();

        List<ShareGrant> active = grants.stream().filter(g -> "ACTIVE".equals(g.status)).toList();
        boolean revoked = active.isEmpty() && grants.stream().anyMatch(g -> "REVOKED".equals(g.status));
        boolean activeDownload = active.stream().anyMatch(g -> "DOWNLOAD".equals(g.permission));

        if (action == Action.SHARE) {
            if (sameTenant && (owner || admin)) {
                return Decision.allow(owner ? "OWNER" : "TENANT_ADMIN");
            }
            if (!sameTenant) {
                return crossTenant();
            }
            return Decision.deny("NOT_OWNER", ACCESS_DENIED, "Access Denied",
                    "Only the document owner or a tenant admin can manage sharing.");
        }

        if (action == Action.VIEW) {
            if (!active.isEmpty()) {
                return Decision.allow(activeDownload ? "SHARED_DOWNLOAD" : "SHARED_VIEW");
            }
            if (sameTenant) {
                return Decision.allow(owner ? "OWNER" : admin ? "TENANT_ADMIN" : "TENANT_MEMBER");
            }
            return revoked ? revokedDecision() : crossTenant();
        }

        // DOWNLOAD (decrypt)
        if (activeDownload) {
            return Decision.allow("SHARED_DOWNLOAD");
        }
        if (revoked) {
            return revokedDecision();                       // explicit revocation always wins
        }
        if (sameTenant) {
            if (owner) return Decision.allow("OWNER");
            if (admin) return Decision.allow("TENANT_ADMIN");
            if (!"CONFIDENTIAL".equals(d.classification)) return Decision.allow("TENANT_MEMBER");
            return Decision.deny("RESTRICTED", ACCESS_DENIED, "Access Denied",
                    "CONFIDENTIAL documents require ownership, admin role, or an explicit share.");
        }
        if (!active.isEmpty()) {
            return Decision.deny("SHARED_VIEW", ACCESS_DENIED, "Access Denied",
                    "This share grants VIEW only, not DOWNLOAD.");
        }
        return crossTenant();
    }

    private static Decision revokedDecision() {
        return Decision.deny("REVOKED", ACCESS_DENIED, "Access Denied", "Your access to this document was revoked.");
    }
}
