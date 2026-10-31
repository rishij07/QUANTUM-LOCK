create table tenants (
  id uuid primary key,
  name varchar(100) not null,
  slug varchar(50) not null unique
);

create table users (
  id uuid primary key,
  email varchar(255) not null unique,
  password_hash varchar(100) not null,
  display_name varchar(100) not null,
  role varchar(20) not null,
  tenant_id uuid not null references tenants(id),
  enabled boolean not null default true
);

create table documents (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  owner_id uuid not null references users(id),
  name varchar(255) not null,
  classification varchar(20) not null,
  content_type varchar(150) not null,
  current_version int not null,
  size_bytes bigint not null,
  tampered_demo boolean not null default false,
  created_at timestamptz not null
);
create index idx_documents_tenant on documents(tenant_id);

-- One row per document version. Each version has its OWN data-encryption key (DEK),
-- stored only in wrapped (KEK-encrypted) form. The ciphertext itself lives in MinIO.
create table document_versions (
  id uuid primary key,
  document_id uuid not null references documents(id),
  tenant_id uuid not null references tenants(id),
  version int not null,
  object_key varchar(300) not null,
  wrapped_dek bytea not null,
  kek_id varchar(60) not null,
  size_bytes bigint not null,
  cipher_bytes bigint not null,
  content_index varchar(64) not null,
  created_by uuid not null references users(id),
  created_at timestamptz not null,
  constraint uq_doc_version unique (document_id, version),
  constraint uq_tenant_content unique (tenant_id, content_index)
);

create table share_grants (
  id uuid primary key,
  document_id uuid not null references documents(id),
  grantee_id uuid not null references users(id),
  granted_by uuid not null references users(id),
  permission varchar(20) not null,
  status varchar(20) not null,
  created_at timestamptz not null,
  revoked_at timestamptz,
  revoked_by uuid references users(id)
);
create index idx_grants_grantee on share_grants(grantee_id);
create index idx_grants_document on share_grants(document_id);

-- Append-only audit trail, hash-chained with SHA-256 (hash = H(prev_hash | fields)).
create table audit_events (
  id bigserial primary key,
  ts timestamptz not null,
  user_email varchar(255) not null,
  tenant_name varchar(100) not null,
  action varchar(40) not null,
  resource varchar(400) not null,
  result varchar(20) not null,
  detail varchar(1000),
  prev_hash varchar(64) not null,
  hash varchar(64) not null
);
create index idx_audit_tenant on audit_events(tenant_name);
create index idx_audit_user on audit_events(user_email);
