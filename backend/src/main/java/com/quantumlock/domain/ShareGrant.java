package com.quantumlock.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "share_grants")
public class ShareGrant {
    @Id public UUID id;
    public UUID documentId;
    public UUID granteeId;
    public UUID grantedBy;
    public String permission; // VIEW | DOWNLOAD
    public String status;     // ACTIVE | REVOKED
    public Instant createdAt;
    public Instant revokedAt;
    public UUID revokedBy;
}
