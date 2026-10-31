package com.quantumlock.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.quantumlock.domain.AppUser;
import com.quantumlock.domain.Tenant;
import com.quantumlock.repo.TenantRepository;
import com.quantumlock.repo.UserRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates the bearer token, then re-loads the user and tenant from the database.
 * Identity (and therefore tenant) always comes from the server side, never from request input.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    public static final String AUTH_ERROR_ATTR = "qlock.authError";

    private final JwtService jwt;
    private final UserRepository users;
    private final TenantRepository tenants;

    public JwtAuthFilter(JwtService jwt, UserRepository users, TenantRepository tenants) {
        this.jwt = jwt;
        this.users = users;
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var claims = jwt.parse(header.substring(7).trim());
                UUID userId = UUID.fromString(claims.getSubject());
                AppUser u = users.findById(userId).filter(x -> x.enabled).orElseThrow(() -> new JwtException("unknown or disabled user"));
                Tenant t = tenants.findById(u.tenantId).orElseThrow(() -> new JwtException("unknown tenant"));
                UserPrincipal p = UserPrincipal.of(u, t);
                var auth = new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                req.setAttribute(AUTH_ERROR_ATTR, e.getClass().getSimpleName());
            }
        }
        chain.doFilter(req, res);
    }
}
