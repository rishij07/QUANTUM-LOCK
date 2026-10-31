package com.quantumlock.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.quantumlock.config.QlockProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(QlockProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = props.jwtTtlMinutes();
    }

    public String issue(UserPrincipal p) {
        Instant now = Instant.now();
        return build(p, now, now.plusSeconds(ttlMinutes * 60));
    }

    /** Validly signed but already expired (used by the Attack Simulation Centre). */
    public String issueExpired(UserPrincipal p) {
        Instant now = Instant.now();
        return build(p, now.minusSeconds(7200), now.minusSeconds(3600));
    }

    /** Valid token whose signature has been altered. */
    public String forgeSignature(String token) {
        int dot = token.lastIndexOf('.');
        String sig = token.substring(dot + 1);
        char first = sig.charAt(0) == 'A' ? 'B' : 'A';
        return token.substring(0, dot + 1) + first + sig.substring(1);
    }

    /** Unsigned token with alg=none (classic JWT downgrade attack). */
    public String unsigned(UserPrincipal p) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"" + p.id() + "\",\"role\":\"ADMIN\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private String build(UserPrincipal p, Instant iat, Instant exp) {
        return Jwts.builder()
                .subject(p.id().toString())
                .claim("email", p.email())
                .claim("role", p.role())
                .claim("tenant", p.tenantName())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(iat))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }
}
