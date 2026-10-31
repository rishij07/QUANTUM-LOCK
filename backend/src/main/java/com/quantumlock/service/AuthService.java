package com.quantumlock.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.quantumlock.config.ApiException;
import com.quantumlock.domain.AppUser;
import com.quantumlock.domain.Tenant;
import com.quantumlock.repo.TenantRepository;
import com.quantumlock.repo.UserRepository;
import com.quantumlock.security.JwtService;
import com.quantumlock.security.UserPrincipal;
import com.quantumlock.service.Dto.LoginResponse;
import com.quantumlock.service.Dto.UserInfo;

@Service
public class AuthService {
    private final UserRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;

    public AuthService(UserRepository users, TenantRepository tenants, PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users;
        this.tenants = tenants;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
    }

    public LoginResponse login(String email, String password) {
        String mail = email == null ? "" : email.trim().toLowerCase();
        AppUser u = users.findByEmailIgnoreCase(mail).orElse(null);
        boolean ok = u != null && u.enabled && password != null && encoder.matches(password, u.passwordHash);
        if (!ok) {
            audit.record(mail.isEmpty() ? "unknown" : mail, "-", "LOGIN", "POST /api/auth/login", "DENIED", "Invalid credentials");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid credentials");
        }
        Tenant t = tenants.findById(u.tenantId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid credentials"));
        UserPrincipal p = UserPrincipal.of(u, t);
        audit.record(p, "LOGIN", "session", "SUCCESS", "JWT issued");
        return new LoginResponse(jwt.issue(p), info(p));
    }

    public static UserInfo info(UserPrincipal p) {
        return new UserInfo(p.id(), p.email(), p.displayName(), p.role(), p.tenantId(), p.tenantName());
    }
}
