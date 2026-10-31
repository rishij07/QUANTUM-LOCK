package com.quantumlock.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_versions")
public class DocumentVersion {
    @Id public UUID id;
    public UUID documentId;
    public UUID tenantId;
    public int version;
    public String objectKey;
    public byte[] wrappedDek;
    public String kekId;
    public long sizeBytes;
    public long cipherBytes;
    public String contentIndex;
    public UUID createdBy;
    public Instant createdAt;
}
