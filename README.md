# QUANTUM-LOCK — Zero-Trust Data Vault

A hackathon MVP that you can run and attack. Documents are encrypted with **AES-256-GCM** using a **unique DEK per document version**, the DEK is wrapped by a **KEK through a KMS abstraction**, only **ciphertext** reaches MinIO, tenants are isolated by identity, access is shareable and revocable, every decision lands in a **SHA-256 hash-chained audit log**, and an **Attack Simulation Centre** proves it.

```
docker compose up --build
```

| What | URL |
|---|---|
| App (React, served by nginx) | http://localhost:3000 |
| API (Spring Boot) | http://localhost:8080 |
| MinIO console (show judges the ciphertext) | http://localhost:9001 — `minioadmin` / `minioadmin` |

First build downloads Maven and npm dependencies, so allow a few minutes. The backend waits for PostgreSQL and MinIO, runs Flyway migrations, creates the bucket and seeds demo data.

## Demo accounts (password for all: `Quantum#2026`)

| User | Tenant | Role |
|---|---|---|
| admin@tenant-a.com | Tenant A | Admin |
| user@tenant-a.com | Tenant A | Employee |
| user@tenant-b.com | Tenant B | Employee |

Seeded documents: Tenant A has `Q3-Financial-Forecast.txt` (CONFIDENTIAL, owned by admin), `Employee-Handbook.txt` and `Project-Atlas-Notes.txt` (INTERNAL). Tenant B has `Tenant-B-Customer-Contracts.txt` (CONFIDENTIAL) and `Tenant-B-Roadmap.txt` (INTERNAL). A hidden "System" tenant holds a canary file used by the revocation attack.

## Architecture (modular monolith)

```
React (Vite) ──nginx /api──▶ Spring Boot 3 / Java 21
                               ├─ security   JWT filter: token → user → tenant loaded from the DB
                               ├─ service    AccessPolicy (single authorization decision point)
                               │             DocumentService (envelope-encryption pipeline)
                               │             ShareService · AuditService · AttackService · SeedService
                               ├─ crypto     CryptoService  AES-256-GCM, IV‖ciphertext‖tag, AAD = doc id + version
                               ├─ kms        KmsService (interface) → LocalKmsService (KEK from env, AES-GCM wrap)
                               ├─ storage    ObjectStore → MinIO (ciphertext only)
                               └─ domain/repo  JPA + Flyway → PostgreSQL
```

### Upload
1. Random 256-bit DEK for this document **version**.
2. AES-256-GCM encrypt with a random IV; AAD binds the blob to `doc:<id>:v<n>` so blobs cannot be swapped between documents.
3. Ciphertext → MinIO at `<tenantId>/<docId>/v<n>.enc`. Plaintext is never written anywhere.
4. DEK wrapped by the KEK via `KmsService.wrap` (also AES-GCM, context-bound). Only the wrapped DEK is stored.
5. Metadata → PostgreSQL. The DEK is zeroed in memory.

### Download
Authenticate JWT → load user and tenant from DB → `AccessPolicy` (tenant, role, classification, share permission, revocation) → fetch ciphertext → unwrap DEK → GCM verify and decrypt → return plaintext. Any failure (policy, storage, key, tag) returns an error and **no plaintext**.

### Authorization rules (`AccessPolicy`)
- Authentication ≠ authorization. Default is deny.
- Tenant always comes from the authenticated user record. No endpoint accepts a tenant id.
- Same tenant: owner and tenant admin can do everything; other members can download PUBLIC and INTERNAL documents; CONFIDENTIAL needs an explicit share.
- Other tenant: denied unless an explicit, ACTIVE share exists. Attempts return `403 — Cross-tenant access blocked` and log `CROSS_TENANT_ATTEMPT`.
- Share permissions: `VIEW` (metadata only) and `DOWNLOAD` (decrypt). Only the owner or a tenant admin can share or revoke.
- A REVOKED share with no ACTIVE replacement is an explicit deny and returns `403 — Access Denied`, even if the user would otherwise have access.

### Audit hash chain
`hash = SHA-256(prev_hash | ts | user | tenant | action | resource | result | detail)`. Events are written in their own transaction under a Postgres advisory lock, so denied attempts persist even when the request rolls back. `GET /api/audit/verify` walks the whole chain. Events: LOGIN, UPLOAD, DOWNLOAD, SHARE, REVOKE, ACCESS_DENIED, CROSS_TENANT_ATTEMPT, TAMPERING_DETECTED, plus DUPLICATE_DETECTED and TAMPER_INJECTED.

### Attack Simulation Centre
Each button runs a real attack against the real code and reports the verdict, HTTP status, steps and why it failed.

| Attack | Verdict | What happens |
|---|---|---|
| Cross-Tenant Access | BLOCKED | Requests a foreign-tenant document by exact ID → 403 |
| Ciphertext Tampering | DETECTED | Flips a byte in an in-memory copy → GCM tag fails (storage is not modified) |
| Revoked User Access | DENIED | Share → download OK → revoke → download 403 |
| Invalid / Expired JWT | DENIED | Expired, forged-signature and `alg=none` tokens, plus a live request with the forged token → 401 |
| Duplicate Upload | DETECTED | Replays an existing file's bytes → 409 via a keyed hash |
| Audit Log Tampering | DETECTED | Forges a past event in memory → hash mismatch |

## Demo hooks that are deliberately not production behaviour
- `QL_DEMO_MODE=true` enables `/api/demo/foreign-docs` (lists other tenants' document IDs so judges can paste one into the probe box) and the per-document bug icon that flips one bit of the stored ciphertext. Set it to `false` to remove both.
- Demo secrets (`QL_KEK_BASE64`, `QL_JWT_SECRET`) are in `docker-compose.yml`. In production the KEK belongs in a real KMS/HSM, which is why `KmsService` is an interface.
- Rate limiting, MFA, large-file streaming and key rotation are out of scope for the MVP.

## Real (non-simulated) audit tampering
```bash
docker compose exec postgres psql -U quantum -d quantumlock \
  -c "UPDATE audit_events SET result='SUCCESS' WHERE id=(SELECT min(id) FROM audit_events WHERE result='DENIED');"
```
Then click **Verify chain** on the Audit Logs page: it reports the first broken event.

## Development without Docker
```bash
docker compose up postgres minio          # infrastructure only
cd backend && mvn spring-boot:run         # http://localhost:8080
cd frontend && npm install && npm run dev # http://localhost:5173 (proxies /api)
```

## API summary
`POST /api/auth/login` · `GET /api/me` · `GET /api/dashboard` · `GET|POST /api/documents` · `GET /api/documents/{id}` · `GET /api/documents/{id}/download` · `POST /api/documents/{id}/versions` · `POST /api/documents/{id}/shares` · `POST /api/shares/{id}/revoke` · `GET /api/shares/outgoing|incoming` · `GET /api/audit` · `GET /api/audit/verify` · `POST /api/audit/simulate-tamper` · `POST /api/attacks/{cross-tenant|tampering|revoked|invalid-jwt|duplicate|audit-tamper}`

---

# How to Demo QUANTUM-LOCK to Judges (5 minutes)

**0:00 – Login and posture (30 s).** Open http://localhost:3000. Sign in as `admin@tenant-a.com`. Point at the dashboard strip: audit chain intact, AES-256-GCM, KEK id, tenant isolation. "Everything you're about to see is enforced on the server."

**0:30 – Upload and encrypt (60 s).** Go to **Upload**, drop any file, choose CONFIDENTIAL, click *Encrypt and store*. Show the receipt: ciphertext size, MinIO object key, wrapped DEK. Open the MinIO console (http://localhost:9001) and show that the object is unreadable ciphertext. Back in **Documents**, use the eye icon to show it decrypts for the owner.

**1:30 – Share, then revoke (60 s).** In a private window sign in as `user@tenant-a.com` and open **Documents**: the CONFIDENTIAL file shows *Restricted* and View returns `403 — Access Denied`. As admin, share it with `user@tenant-a.com` (DOWNLOAD) in **Sharing & Access**. The employee can now view it. Click **Revoke** and retry as the employee: `403 — Access Denied`, enforced by the backend.

**2:30 – Cross-tenant isolation (45 s).** Sign in as `user@tenant-b.com` in a second window, or stay as Tenant A. On **Documents**, click *Use another tenant's document* in the probe panel, then *Request download*: `403 — Cross-tenant access blocked`.

**3:15 – Tampering (45 s).** As admin on **Documents**, click the bug icon on a document. That flips one bit of the ciphertext in MinIO. Click View: **TAMPERING DETECTED — AUTHENTICATION FAILED**, no corrupted plaintext. Click the bug icon again to restore it.

**4:00 – Attack Simulation Centre (45 s).** Open **Attack Simulation** and press **Run all attacks**. Walk down the BLOCKED / DENIED / DETECTED verdicts and read one "why it failed" line aloud.

**4:45 – Audit proof (15 s).** Open **Audit Logs**: every step above is there. Click **Verify chain** (intact), then **Simulate log tampering** to show a forged event breaking the SHA-256 chain.
