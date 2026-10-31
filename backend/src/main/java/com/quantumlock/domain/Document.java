package com.quantumlock.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class Document {
    @Id public UUID id;
    public UUID tenantId;
    public UUID ownerId;
    public String name;
    public String classification; // PUBLIC | INTERNAL | CONFIDENTIAL
    public String contentType;
    public int currentVersion;
    public long sizeBytes;
    public boolean tamperedDemo;
    public Instant createdAt;
}
