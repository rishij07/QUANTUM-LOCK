package com.quantumlock.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.quantumlock.config.ApiException;
import com.quantumlock.config.QlockProperties;
import com.quantumlock.config.TamperingException;
import com.quantumlock.crypto.CryptoService;
import com.quantumlock.domain.AppUser;
import com.quantumlock.domain.Document;
import com.quantumlock.domain.DocumentVersion;
import com.quantumlock.domain.ShareGrant;
import com.quantumlock.domain.Tenant;
import com.quantumlock.kms.KmsService;
import com.quantumlock.repo.DocumentRepository;
import com.quantumlock.repo.DocumentVersionRepository;
import com.quantumlock.repo.ShareGrantRepository;
import com.quantumlock.repo.TenantRepository;
import com.quantumlock.repo.UserRepository;
import com.quantumlock.security.UserPrincipal;
import com.quantumlock.service.AccessPolicy.Action;
import com.quantumlock.service.AccessPolicy.Decision;
import com.quantumlock.service.Dto.DocView;
import com.quantumlock.service.Dto.UploadReceipt;
import com.quantumlock.storage.ObjectStore;

/** Envelope-encryption document pipeline: per-version DEK -> AES-256-GCM -> MinIO; DEK wrapped by the KMS. */
@Service
public class DocumentService {
    private static final Set<String> CLASSIFICATIONS = Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL");

    private final DocumentRepository docs;
    private final DocumentVersionRepository versions;
    private final ShareGrantRepository grants;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final CryptoService crypto;
    private final KmsService kms;
    private final ObjectStore store;
    private final AuditService audit;
    private final AccessPolicy policy;
    private final QlockProperties props;

    public DocumentService(DocumentRepository docs, DocumentVersionRepository versions, ShareGrantRepository grants,
                           UserRepository users, TenantRepository tenants, CryptoService crypto, KmsService kms,
                           ObjectStore store, AuditService audit, AccessPolicy policy, QlockProperties props) {
        this.docs = docs;
        this.versions = versions;
        this.grants = grants;
        this.users = users;
        this.tenants = tenants;
        this.crypto = crypto;
        this.kms = kms;
        this.store = store;
        this.audit = audit;
        this.policy = policy;
        this.props = props;
    }

    public record Download(Document doc, DocumentVersion version, byte[] plaintext) {}

    public record TamperOutcome(String documentName, boolean baselineOk, boolean detected, int flippedByteOffset) {}

    // ------------------------------------------------------------------ upload

    @Transactional
    public UploadReceipt upload(UserPrincipal u, String filename, String contentType, String classification, byte[] data) {
        String cls = classification == null || classification.isBlank() ? "INTERNAL" : classification.toUpperCase();
        if (!CLASSIFICATIONS.contains(cls)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_CLASSIFICATION", "Classification must be PUBLIC, INTERNAL or CONFIDENTIAL");
        }
        if (data == null || data.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "File is empty");
        }
        String name = cleanName(filename);
        String idx = rejectDuplicate(u, name, data);

        Document d = new Document();
        d.id = UUID.randomUUID();
        d.tenantId = u.tenantId();                   // tenant from identity, never from the request
        d.ownerId = u.id();
        d.name = name;
        d.classification = cls;
        d.contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        d.currentVersion = 1;
        d.sizeBytes = data.length;
        d.createdAt = Instant.now();
        docs.save(d);

        DocumentVersion v = storeVersion(u, d, 1, data, idx);
        audit.record(u, "UPLOAD", label(d), "SUCCESS",
                "v1 encrypted with AES-256-GCM, DEK wrapped by " + v.kekId + ", " + v.cipherBytes + " ciphertext bytes stored");
        return receipt(u, d, v);
    }

    @Transactional
    public UploadReceipt uploadNewVersion(UserPrincipal u, UUID docId, byte[] data) {
        Document d = find(docId);
        authorize(u, d, Action.SHARE, "NEW_VERSION", null);
        if (data == null || data.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "File is empty");
        }
        String idx = rejectDuplicate(u, d.name, data);
        int next = d.currentVersion + 1;
        DocumentVersion v = storeVersion(u, d, next, data, idx);
        d.currentVersion = next;
        d.sizeBytes = data.length;
        d.tamperedDemo = false;
        docs.save(d);
        audit.record(u, "UPLOAD", label(d), "SUCCESS", "v" + next + " encrypted with a NEW DEK (per-version keys)");
        return receipt(u, d, v);
    }

    private String rejectDuplicate(UserPrincipal u, String name, byte[] data) {
        String idx = kms.blindIndex(data, u.tenantId().toString());
        if (versions.existsByTenantIdAndContentIndex(u.tenantId(), idx)) {
            audit.record(u, "DUPLICATE_DETECTED", name, "DETECTED", "Identical content already stored in this tenant; upload rejected");
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_DETECTED", "DUPLICATE DETECTED \u2014 identical content already stored",
                    "A keyed hash of the plaintext matches an existing version in your tenant.");
        }
        return idx;
    }

    private DocumentVersion storeVersion(UserPrincipal u, Document d, int version, byte[] plaintext, String contentIndex) {
        byte[] dek = crypto.newDek();                                  // 1. unique random DEK for THIS version
        try {
            byte[] aad = aad(d.id, version);
            byte[] blob = crypto.encrypt(plaintext, dek, aad);        // 2. AES-256-GCM
            byte[] wrapped = kms.wrap(dek, ctx(d.id, version));       // 4. KEK wraps the DEK via the KMS
            String key = d.tenantId + "/" + d.id + "/v" + version + ".enc";
            store.put(key, blob);                                      // 3. ciphertext only goes to MinIO

            DocumentVersion v = new DocumentVersion();
            v.id = UUID.randomUUID();
            v.documentId = d.id;
            v.tenantId = d.tenantId;
            v.version = version;
            v.objectKey = key;
            v.wrappedDek = wrapped;
            v.kekId = kms.activeKeyId();
            v.sizeBytes = plaintext.length;
            v.cipherBytes = blob.length;
            v.contentIndex = contentIndex;
            v.createdBy = u.id();
            v.createdAt = Instant.now();
            return versions.save(v);                                   // 5. metadata in PostgreSQL
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    // ---------------------------------------------------------------- download

    public Download download(UserPrincipal u, UUID docId, Integer version, String note) {
        Document d = find(docId);
        authorize(u, d, Action.DOWNLOAD, "DOWNLOAD", note);                      // 1-4: identity, tenant, permission, revocation
        int vn = version == null ? d.currentVersion : version;
        DocumentVersion v = versions.findByDocumentIdAndVersion(d.id, vn)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", "Version not found"));

        byte[] blob = store.get(v.objectKey);                                    // 5. encrypted object
        byte[] dek = kms.unwrap(v.wrappedDek, v.kekId, ctx(d.id, v.version));    // 6. protected DEK -> DEK (fails closed)
        try {
            byte[] plain = crypto.decrypt(blob, dek, aad(d.id, v.version));      // 7. AES-256-GCM verify + decrypt
            audit.record(u, "DOWNLOAD", label(d), "SUCCESS", "v" + v.version + " decrypted after authorization" + suffix(note));
            return new Download(d, v, plain);                                    // 8. plaintext only after success
        } catch (TamperingException e) {
            audit.record(u, "TAMPERING_DETECTED", label(d), "DETECTED",
                    "GCM authentication failed on v" + v.version + "; plaintext withheld" + suffix(note));
            throw e;
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    // ------------------------------------------------------------- tampering demo

    /** Demo: toggles a single bit of the stored ciphertext in MinIO (XOR is its own inverse, so call twice to restore). */
    public DocView toggleTamper(UserPrincipal u, UUID docId) {
        if (!props.demoMode()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
        }
        Document d = find(docId);
        authorize(u, d, Action.SHARE, "TAMPER_DEMO", null);
        DocumentVersion v = versions.findByDocumentIdAndVersion(d.id, d.currentVersion)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", "Version not found"));
        byte[] blob = store.get(v.objectKey);
        if (blob.length <= CryptoService.IV_BYTES + 16) {
            throw new ApiException(HttpStatus.CONFLICT, "TOO_SMALL", "Object too small to tamper");
        }
        blob[CryptoService.IV_BYTES] ^= 0x01;           // flip one bit of the first ciphertext byte
        store.put(v.objectKey, blob);
        d.tamperedDemo = !d.tamperedDemo;
        docs.save(d);
        audit.record(u, "TAMPER_INJECTED", label(d), "SUCCESS",
                d.tamperedDemo ? "Demo: 1 bit flipped in stored ciphertext" : "Demo: ciphertext restored");
        return view(u, d, grants.findByDocumentIdAndGranteeId(d.id, u.id()), ownerMap(List.of(d)), tenantMap(List.of(d)));
    }

    /** Attack simulation: flips a byte of an in-memory copy of the ciphertext and tries to decrypt it. Storage is untouched. */
    public TamperOutcome simulateCiphertextTamper(UserPrincipal u, Document d) {
        authorize(u, d, Action.DOWNLOAD, "TAMPER_SIM", "[SIM]");
        DocumentVersion v = versions.findByDocumentIdAndVersion(d.id, d.currentVersion)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", "Version not found"));
        byte[] blob = store.get(v.objectKey);
        byte[] dek = kms.unwrap(v.wrappedDek, v.kekId, ctx(d.id, v.version));
        byte[] aad = aad(d.id, v.version);
        try {
            boolean baselineOk = true;
            try {
                crypto.decrypt(blob, dek, aad);
            } catch (TamperingException e) {
                baselineOk = false;
            }
            byte[] forged = blob.clone();
            int offset = forged.length - 17;                   // last ciphertext byte (just before the 16-byte tag)
            forged[offset] ^= 0x5A;
            boolean detected = false;
            try {
                crypto.decrypt(forged, dek, aad);
            } catch (TamperingException e) {
                detected = true;
                audit.record(u, "TAMPERING_DETECTED", label(d), "DETECTED", "[SIM] ciphertext byte " + offset + " modified; GCM tag check failed");
            }
            return new TamperOutcome(d.name, baselineOk, detected, offset);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    // ---------------------------------------------------------------- listing

    public List<DocView> list(UserPrincipal u) {
        List<Document> visible = docs.findVisible(u.tenantId(), u.id());
        Map<UUID, List<ShareGrant>> byDoc = grants.findByGranteeId(u.id()).stream().collect(Collectors.groupingBy(g -> g.documentId));
        Map<UUID, AppUser> owners = ownerMap(visible);
        Map<UUID, Tenant> tens = tenantMap(visible);
        return visible.stream().map(d -> view(u, d, byDoc.getOrDefault(d.id, List.of()), owners, tens)).toList();
    }

    public DocView get(UserPrincipal u, UUID docId) {
        Document d = find(docId);
        List<ShareGrant> g = authorize(u, d, Action.VIEW, "VIEW", null);
        return view(u, d, g, ownerMap(List.of(d)), tenantMap(List.of(d)));
    }

    /** First same-tenant document this user may decrypt (used by attack simulations). */
    public Optional<Document> pickOwnTenantDownloadable(UserPrincipal u) {
        return list(u).stream().filter(v -> v.canDownload() && !v.external() && !v.tampered()).findFirst()
                .flatMap(v -> docs.findById(v.id()));
    }

    // ---------------------------------------------------------------- helpers

    public Document find(UUID id) {
        return docs.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document not found"));
    }

    /** Evaluates the policy; audits and throws 403 when refused. Returns the caller's grants on success. */
    public List<ShareGrant> authorize(UserPrincipal u, Document d, Action action, String op, String note) {
        List<ShareGrant> g = grants.findByDocumentIdAndGranteeId(d.id, u.id());
        Decision dec = policy.evaluate(u, d, action, g);
        if (!dec.allowed()) {
            boolean cross = AccessPolicy.CROSS_TENANT.equals(dec.code());
            audit.record(u, cross ? "CROSS_TENANT_ATTEMPT" : "ACCESS_DENIED",
                    cross ? "doc:" + d.id : label(d),            // never leak a foreign document's name
                    cross ? "BLOCKED" : "DENIED",
                    op + " refused (" + dec.basis() + ")" + suffix(note));
            throw new ApiException(HttpStatus.FORBIDDEN, dec.code(), dec.message(), dec.detail());
        }
        return g;
    }

    public DocView view(UserPrincipal u, Document d, List<ShareGrant> g, Map<UUID, AppUser> owners, Map<UUID, Tenant> tens) {
        Decision dl = policy.evaluate(u, d, Action.DOWNLOAD, g);
        Decision sh = policy.evaluate(u, d, Action.SHARE, g);
        AppUser owner = owners.get(d.ownerId);
        Tenant t = tens.get(d.tenantId);
        return new DocView(d.id, d.name, owner == null ? "?" : owner.email, owner == null ? "?" : owner.displayName,
                t == null ? "?" : t.name, d.classification, d.createdAt, d.currentVersion, d.sizeBytes, d.contentType,
                dl.basis(), dl.allowed(), sh.allowed(), !d.tenantId.equals(u.tenantId()), d.tamperedDemo);
    }

    private UploadReceipt receipt(UserPrincipal u, Document d, DocumentVersion v) {
        DocView dv = view(u, d, List.of(), ownerMap(List.of(d)), tenantMap(List.of(d)));
        String preview = HexFormat.of().formatHex(v.wrappedDek, 0, Math.min(10, v.wrappedDek.length)) + "\u2026";
        return new UploadReceipt(dv, v.version, v.objectKey, v.kekId, "AES-256-GCM", v.sizeBytes, v.cipherBytes, preview);
    }

    private Map<UUID, AppUser> ownerMap(List<Document> ds) {
        Map<UUID, AppUser> m = new HashMap<>();
        users.findAllById(ds.stream().map(d -> d.ownerId).collect(Collectors.toSet())).forEach(x -> m.put(x.id, x));
        return m;
    }

    private Map<UUID, Tenant> tenantMap(List<Document> ds) {
        Map<UUID, Tenant> m = new HashMap<>();
        tenants.findAllById(ds.stream().map(d -> d.tenantId).collect(Collectors.toSet())).forEach(x -> m.put(x.id, x));
        return m;
    }

    private static String label(Document d) {
        return d.name + " [" + d.id.toString().substring(0, 8) + "]";
    }

    private static String suffix(String note) {
        return note == null || note.isBlank() ? "" : " " + note;
    }

    private static String ctx(UUID docId, int version) {
        return "doc:" + docId + ":v" + version;
    }

    private static byte[] aad(UUID docId, int version) {
        return ctx(docId, version).getBytes(StandardCharsets.UTF_8);
    }

    private static String cleanName(String filename) {
        String n = filename == null ? "untitled" : filename.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (n.isEmpty()) n = "untitled";
        return n.length() > 200 ? n.substring(0, 200) : n;
    }
}
