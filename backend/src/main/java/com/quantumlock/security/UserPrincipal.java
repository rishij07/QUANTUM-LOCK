package com.quantumlock.security;

import java.util.UUID;

import com.quantumlock.domain.AppUser;
import com.quantumlock.domain.Tenant;

/** The authenticated identity. Tenant is derived from the DB row, never from the request. */
public record UserPrincipal(UUID id, String email, String displayName, String role, UUID tenantId, String tenantName) {
    public static UserPrincipal of(AppUser u, Tenant t) {
        return new UserPrincipal(u.id, u.email, u.displayName, u.role, u.tenantId, t.name);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
