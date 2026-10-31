package com.quantumlock.domain;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class AppUser {
    @Id public UUID id;
    public String email;
    public String passwordHash;
    public String displayName;
    public String role;       // ADMIN | EMPLOYEE
    public UUID tenantId;
    public boolean enabled;
}
