<div align="center">

# 🔐 QUANTUM-LOCK

### Zero-Trust Data Vault: encrypted, tenant-isolated, revocable and provably tamper-evident

**🎥 [Watch the 2-minute demo](YOUR_VIDEO_LINK)**

`Java 21` · `Spring Boot 3` · `Spring Security + JWT` · `PostgreSQL` · `MinIO` · `AES-256-GCM` · `React + Vite` · `Docker Compose`

</div>

---

## 💡 The problem

Shared document platforms get breached in predictable ways: a leaked account reads another company's files, a revoked employee keeps access, a storage admin edits a file silently, or someone rewrites the logs to hide it. Encrypting files alone does not fix any of these.

## ✅ What QUANTUM-LOCK does

| Threat | How QUANTUM-LOCK stops it |
|---|---|
| Storage is stolen or read | Only **ciphertext** is ever stored in MinIO. Every document version has its own random key (**envelope encryption**, AES-256-GCM). |
| Key theft | Data keys are **wrapped by a master key** through a KMS abstraction. The master key never sits next to the data. |
| One tenant reads another's file | **Tenant comes from the authenticated user, never from the request.** Even the exact document ID returns `403 — Cross-tenant access blocked`. |
| Ex-employee keeps access | **Revocation is enforced in the backend** on every request, not hidden in the UI. |
| File modified in storage | AES-GCM authentication fails → **TAMPERING DETECTED**, and no corrupted plaintext is ever returned. |
| Forged or expired login token | Signature and expiry are checked before any business logic runs. |
| Someone rewrites the audit log | Every event is **SHA-256 hash-chained**. The chain verifier reports the first broken event. |
| Something fails halfway | The system **fails closed**: no key, no storage or no authorization means no data. |

## 📸 Screenshots


## 🚀 Run it (about 3 minutes)

**Prerequisite:** [Docker Desktop](https://www.docker.com/products/docker-desktop/)

```bash
git clone https://github.com/rishij07/QUANTUM-LOCK.git
cd QUANTUM-LOCK
docker compose up --build
```

The first build downloads dependencies, so allow a few minutes. Then open:

| What | URL |
|---|---|
| **App** | http://localhost:3000 |
| API (Spring Boot) | http://localhost:8080 |
| MinIO console (see the encrypted files) | http://localhost:9001 · `minioadmin` / `minioadmin` |

### Demo accounts (password for all: `Quantum#2026`)

| User | Tenant | Role |
|---|---|---|
| `admin@tenant-a.com` | Tenant A | Admin |
| `user@tenant-a.com` | Tenant A | Employee |
| `user@tenant-b.com` | Tenant B | Employee |

Sample documents are seeded automatically for both tenants, so cross-tenant isolation can be shown right away.

## 🎯 The Attack Simulation Centre

A built-in page where judges can **attack the system live**. Each button runs a real attack against the real code and shows the verdict, HTTP status, the steps taken and why it failed.

| Attack | Verdict | What happens |
|---|---|---|
| Cross-Tenant Access | 🛑 BLOCKED | Requests another tenant's document by its exact ID → 403 |
| Ciphertext Tampering | 🔎 DETECTED | Flips a byte of the encrypted file → GCM authentication fails |
| Revoked User Access | ⛔ DENIED | Share → download works → revoke → download returns 403 |
| Invalid / Expired JWT | ⛔ DENIED | Expired, forged-signature and `alg=none` tokens are all refused (401) |
| Duplicate Upload | 🔎 DETECTED | Re-uploading identical content is rejected (409) |
| Audit Log Tampering | 🔎 DETECTED | A forged past event breaks the SHA-256 chain |

## 🧭 Features

- Login with JWT; every user belongs to a tenant
- Dashboard with security status, recent uploads, shares and security events
- Upload → encrypt → store, with a receipt showing ciphertext size, MinIO object key and wrapped key
- Document list with owner, classification, version and access status
- Download/view with a full authorization chain before any decryption
- Sharing with permissions (`VIEW` / `DOWNLOAD`) and instant revocation
- Audit log: LOGIN, UPLOAD, DOWNLOAD, SHARE, REVOKE, ACCESS_DENIED, CROSS_TENANT_ATTEMPT, TAMPERING_DETECTED
- Hash-chain verification and a non-destructive tamper simulation

## 🏗️ Architecture (modular monolith)

```
React (Vite) ──nginx /api──▶ Spring Boot 3 / Java 21
                               ├─ security   JWT filter: token → user → tenant loaded from the DB
                               ├─ service    AccessPolicy (single authorization decision point)
                               │             DocumentService (envelope-encryption pipeline)
                               │             ShareService · AuditService · AttackService · SeedService
                               ├─ crypto     AES-256-GCM, IV‖ciphertext‖tag, AAD binds document id + version
                               ├─ kms        KmsService (interface) → LocalKmsService (KEK from env)
                               ├─ storage    ObjectStore → MinIO (ciphertext only)
                               └─ domain/repo JPA + Flyway → PostgreSQL
```

**Upload:** random 256-bit key per version → AES-256-GCM encrypt → ciphertext to MinIO → key wrapped by the KEK → metadata to PostgreSQL → key zeroed in memory.

**Download:** verify JWT → load user and tenant from the database → authorization policy (tenant, role, classification, sharing, revocation) → fetch ciphertext → unwrap key → GCM verify and decrypt → return plaintext. Any failure returns an error and no plaintext.

### Authorization rules

- Authentication ≠ authorization, and the default is **deny**.
- Same tenant: owner and admin have full rights; other members can read PUBLIC and INTERNAL documents; CONFIDENTIAL needs an explicit share.
- Other tenant: denied unless an explicit, active share exists.
- A revoked share is an explicit deny, even if the user would otherwise have access.

## 🎬 5-minute judge demo

1. **Login** as `admin@tenant-a.com` and read the security strip on the dashboard.
2. **Upload** a CONFIDENTIAL file, show the receipt, then open the MinIO console to show the stored object is unreadable ciphertext.
3. **Share and revoke:** as `user@tenant-a.com` the file is restricted (403). Share it as admin → it opens. Revoke → 403 again.
4. **Cross-tenant:** use "Use another tenant's document" → `403 — Cross-tenant access blocked`.
5. **Tampering:** click the bug icon, then View → **TAMPERING DETECTED**. Click again to restore.
6. **Attack Simulation Centre:** press **Run all attacks**.
7. **Audit Logs:** **Verify chain**, then **Simulate log tampering**.

## ⚠️ Honest limitations

This is a hackathon MVP. We would rather state what it does not do:

- The master key (KEK) comes from an environment variable. A real deployment would use a KMS or HSM, which is why `KmsService` is an interface.
- Demo secrets in `docker-compose.yml` are for the hackathon only; all data is synthetic.
- No key rotation, rate limiting or MFA yet.
- Files are processed in memory (25 MB limit).
- Plaintext that a user has already downloaded cannot be taken back; revocation stops **new** retrievals.
- `QL_DEMO_MODE=true` enables demo-only helpers (listing other tenants' document IDs and flipping a bit of stored ciphertext). Set it to `false` to remove them.

## 🔌 API summary

`POST /api/auth/login` · `GET /api/me` · `GET /api/dashboard` · `GET|POST /api/documents` · `GET /api/documents/{id}/download` · `POST /api/documents/{id}/versions` · `POST /api/documents/{id}/shares` · `POST /api/shares/{id}/revoke` · `GET /api/shares/outgoing|incoming` · `GET /api/audit` · `GET /api/audit/verify` · `POST /api/audit/simulate-tamper` · `POST /api/attacks/{cross-tenant|tampering|revoked|invalid-jwt|duplicate|audit-tamper}`

## 🛠️ Development without Docker

```bash
docker compose up postgres minio          # infrastructure only
cd backend && mvn spring-boot:run         # http://localhost:8080
cd frontend && npm install && npm run dev # http://localhost:5173
```

---

<div align="center">

**Built for HACKVERSE 2026** · Build. Innovate. Impact.

</div>
