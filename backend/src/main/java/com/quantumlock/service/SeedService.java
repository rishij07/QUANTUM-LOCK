package com.quantumlock.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.quantumlock.domain.AppUser;
import com.quantumlock.domain.Tenant;
import com.quantumlock.repo.TenantRepository;
import com.quantumlock.repo.UserRepository;
import com.quantumlock.security.UserPrincipal;

/** Idempotent demo data: 2 tenants, 3 demo users, sample encrypted documents, and a hidden "System" tenant for attack canaries. */
@Component
public class SeedService implements ApplicationRunner {
    public static final String DEMO_PASSWORD = "Quantum#2026";
    private static final Logger log = LoggerFactory.getLogger(SeedService.class);

    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final DocumentService documents;

    public SeedService(TenantRepository tenants, UserRepository users, PasswordEncoder encoder, DocumentService documents) {
        this.tenants = tenants;
        this.users = users;
        this.encoder = encoder;
        this.documents = documents;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenants.count() > 0) {
            log.info("Seed data already present - skipping");
            return;
        }
        Tenant a = tenant("Tenant A", "tenant-a");
        Tenant b = tenant("Tenant B", "tenant-b");
        Tenant sys = tenant("System", AttackService.SYSTEM_SLUG);

        AppUser admin = user("admin@tenant-a.com", "Alice Admin", "ADMIN", a, true, DEMO_PASSWORD);
        AppUser emp = user("user@tenant-a.com", "Arjun Employee", "EMPLOYEE", a, true, DEMO_PASSWORD);
        AppUser bEmp = user("user@tenant-b.com", "Bela Employee", "EMPLOYEE", b, true, DEMO_PASSWORD);
        AppUser bot = user(AttackService.BOT_EMAIL, "Vault Bot", "ADMIN", sys, false, UUID.randomUUID().toString());

        put(admin, a, "Q3-Financial-Forecast.txt", "CONFIDENTIAL", """
                TENANT A - CONFIDENTIAL
                Q3 Financial Forecast
                Revenue (projected): 4.2M   Opex: 2.9M   Runway: 19 months
                Pending acquisition target: Project Falcon (do not disclose).
                """);
        put(admin, a, "Employee-Handbook.txt", "INTERNAL", """
                TENANT A - INTERNAL
                Employee Handbook v7
                Working hours, leave policy, security awareness and incident reporting procedure.
                """);
        put(emp, a, "Project-Atlas-Notes.txt", "INTERNAL", """
                TENANT A - INTERNAL
                Project Atlas sprint notes
                - Ship vault sharing UI
                - Review revocation flow
                """);
        put(bEmp, b, "Tenant-B-Customer-Contracts.txt", "CONFIDENTIAL", """
                TENANT B - CONFIDENTIAL
                Customer contract register
                Northwind: 14-month term, 480k.   Contoso: renewal pending.
                This document must NEVER be readable by Tenant A.
                """);
        put(bEmp, b, "Tenant-B-Roadmap.txt", "INTERNAL", """
                TENANT B - INTERNAL
                2027 product roadmap: analytics module, SSO, regional data residency.
                """);
        put(bot, sys, AttackService.CANARY_NAME, "CONFIDENTIAL", """
                SYSTEM CANARY
                Used by the Attack Simulation Centre to demonstrate share -> revoke -> denied.
                """);
        log.info("Seeded demo data. Demo password: {}", DEMO_PASSWORD);
    }

    private Tenant tenant(String name, String slug) {
        Tenant t = new Tenant();
        t.id = UUID.randomUUID();
        t.name = name;
        t.slug = slug;
        return tenants.save(t);
    }

    private AppUser user(String email, String name, String role, Tenant t, boolean enabled, String password) {
        AppUser u = new AppUser();
        u.id = UUID.randomUUID();
        u.email = email;
        u.displayName = name;
        u.role = role;
        u.tenantId = t.id;
        u.enabled = enabled;
        u.passwordHash = encoder.encode(password);
        return users.save(u);
    }

    private void put(AppUser owner, Tenant t, String name, String classification, String content) {
        documents.upload(UserPrincipal.of(owner, t), name, "text/plain", classification, content.getBytes(StandardCharsets.UTF_8));
    }
}
