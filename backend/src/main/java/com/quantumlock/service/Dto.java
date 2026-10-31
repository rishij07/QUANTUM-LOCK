package com.quantumlock.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API response/request shapes. */
public final class Dto {
    private Dto() {}

    public record LoginRequest(String email, String password) {}

    public record UserInfo(UUID id, String email, String displayName, String role, UUID tenantId, String tenantName) {}

    public record LoginResponse(String token, UserInfo user) {}

    public record DocView(UUID id, String name, String ownerEmail, String ownerName, String tenantName,
                          String classification, Instant createdAt, int version, long sizeBytes, String contentType,
                          String access, boolean canDownload, boolean canShare, boolean external, boolean tampered) {}

    public record UploadReceipt(DocView doc, int version, String objectKey, String kekId, String algorithm,
                                long plainBytes, long cipherBytes, String wrappedDekPreview) {}

    public record ShareRequest(String email, String permission) {}

    public record ShareRow(UUID id, UUID documentId, String documentName, String sharedWithEmail, String sharedWithTenant,
                           String sharedByEmail, String permission, String status, Instant createdAt, Instant revokedAt) {}

    public record AuditRow(Long id, Instant ts, String user, String tenant, String action, String resource,
                           String result, String detail, String hash, String prevHash) {}

    public record ChainStatus(boolean valid, long total, Long brokenAtId, String message) {}

    public record TamperSimulation(Long targetId, String action, String originalResult, String forgedResult,
                                   String storedHash, String recomputedHash, boolean detected, Long brokenAtId,
                                   String explanation) {}

    public record AttackResult(String attack, String title, String verdict, int httpStatus, String summary,
                               List<String> steps, String explanation, String probeToken) {}

    public record DashboardView(UserInfo user, long totalDocuments, long sharedWithMe, long sharedByMe,
                                long securityEvents24h, long totalEvents24h, List<DocView> recentUploads,
                                List<AuditRow> recentEvents, ChainStatus chain, String keyId, boolean demoMode) {}

    public record ForeignDoc(UUID id, String name, String tenantName) {}
}
