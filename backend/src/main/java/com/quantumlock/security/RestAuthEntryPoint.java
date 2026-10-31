package com.quantumlock.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.quantumlock.service.AuditService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {
    private final AuditService audit;

    public RestAuthEntryPoint(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        Object err = req.getAttribute(JwtAuthFilter.AUTH_ERROR_ATTR);
        if (err != null) {
            audit.record("anonymous", "-", "ACCESS_DENIED", req.getMethod() + " " + req.getRequestURI(), "DENIED",
                    "Invalid or expired JWT rejected (" + err + ")");
        }
        res.setStatus(401);
        res.setContentType("application/json");
        res.getWriter().write("{\"status\":401,\"code\":\"UNAUTHENTICATED\",\"message\":\"Invalid, expired or missing token\","
                + "\"detail\":\"Request rejected before reaching any business logic.\",\"timestamp\":\"" + Instant.now() + "\"}");
    }
}
