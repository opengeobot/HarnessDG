-- V1__identity_access.sql
-- 身份与访问地基（02 章全量 + 03 §2.1 namespaces）。
-- 业务时间 TIMESTAMPTZ、计数 BIGINT、外部标识 UUID public_id、乐观锁 version（03 §1）。

CREATE EXTENSION IF NOT EXISTS citext;

-- ---------- 用户 ----------
CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    public_id      UUID UNIQUE NOT NULL,
    username       CITEXT UNIQUE NOT NULL,
    password_hash  VARCHAR(128) NOT NULL,
    nickname       VARCHAR(128),
    status         VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'locked', 'disabled')),
    auth_version   BIGINT NOT NULL DEFAULT 1,
    failed_attempts INT NOT NULL DEFAULT 0,
    profile_version BIGINT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- 命名空间（个人/组织统一，03 §2.1） ----------
CREATE TABLE namespaces (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID UNIQUE NOT NULL,
    namespace_type  VARCHAR(16) NOT NULL CHECK (namespace_type IN ('user', 'organization')),
    user_id         BIGINT REFERENCES users(id),
    organization_id BIGINT,
    slug            CITEXT UNIQUE NOT NULL,
    display_name    VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((namespace_type = 'user' AND user_id IS NOT NULL AND organization_id IS NULL)
        OR (namespace_type = 'organization' AND organization_id IS NOT NULL AND user_id IS NULL))
);
CREATE UNIQUE INDEX uq_namespaces_user ON namespaces(user_id) WHERE namespace_type = 'user';

-- ---------- 组织 ----------
CREATE TABLE organizations (
    id                 BIGSERIAL PRIMARY KEY,
    public_id          UUID UNIQUE NOT NULL,
    slug               CITEXT UNIQUE NOT NULL,
    name               VARCHAR(128) NOT NULL,
    description        TEXT,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    status             VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    version            BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE namespaces
    ADD CONSTRAINT fk_namespaces_organization FOREIGN KEY (organization_id) REFERENCES organizations(id);
CREATE UNIQUE INDEX uq_namespaces_org ON namespaces(organization_id) WHERE namespace_type = 'organization';

-- ---------- 组织成员（02 §2：UNIQUE(organization_id,user_id)） ----------
CREATE TABLE organization_memberships (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    role            VARCHAR(16) NOT NULL CHECK (role IN ('owner', 'admin', 'member', 'viewer')),
    status          VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    granted_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_membership_org_user UNIQUE (organization_id, user_id)
);
CREATE INDEX ix_membership_user ON organization_memberships(user_id);

-- ---------- 平台角色（02 §2：持久化且全量审计，禁止 username==demo 旁路） ----------
CREATE TABLE platform_role_assignments (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    role       VARCHAR(32) NOT NULL CHECK (role IN ('platform_admin', 'platform_auditor')),
    granted_by BIGINT REFERENCES users(id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);
CREATE INDEX ix_platform_role_user ON platform_role_assignments(user_id);

-- ---------- Refresh 会话（02 §6.1：family 轮换 + 重放吊销，服务端只存哈希） ----------
CREATE TABLE refresh_sessions (
    id               BIGSERIAL PRIMARY KEY,
    session_id       UUID UNIQUE NOT NULL,
    user_id          BIGINT NOT NULL REFERENCES users(id),
    family_id        UUID NOT NULL,
    token_hash       VARCHAR(64) UNIQUE NOT NULL,
    csrf_token_hash  VARCHAR(64) NOT NULL,
    auth_version     BIGINT NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ,
    replaced_by      BIGINT REFERENCES refresh_sessions(id),
    client_meta      VARCHAR(512),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_sessions_user ON refresh_sessions(user_id);
CREATE INDEX ix_refresh_sessions_family ON refresh_sessions(family_id);

-- ---------- API Key（02 §7：明文只返回一次，服务端存 HMAC 哈希） ----------
CREATE TABLE api_keys (
    id           BIGSERIAL PRIMARY KEY,
    public_id    UUID UNIQUE NOT NULL,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    name         VARCHAR(128) NOT NULL,
    key_prefix   VARCHAR(32) NOT NULL,
    key_hash     VARCHAR(128) NOT NULL,
    scopes       VARCHAR(512) NOT NULL,
    expires_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_api_keys_prefix ON api_keys(key_prefix);
CREATE INDEX ix_api_keys_user ON api_keys(user_id);

-- ---------- 审计日志（02 §2：只追加） ----------
CREATE TABLE audit_logs (
    id         BIGSERIAL PRIMARY KEY,
    actor      VARCHAR(128) NOT NULL,
    action     VARCHAR(64) NOT NULL,
    resource   VARCHAR(256),
    result     VARCHAR(16) NOT NULL,
    trace_id   VARCHAR(64),
    ip         VARCHAR(64),
    user_agent VARCHAR(512),
    details    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_logs_created ON audit_logs(created_at);
CREATE INDEX ix_audit_logs_action ON audit_logs(action);

-- ---------- Idempotency-Key 记录（04 §10，shared.JdbcIdempotencyService） ----------
CREATE TABLE idempotency_records (
    scope            VARCHAR(64) NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    request_hash     VARCHAR(64),
    response_status  INT,
    response_body    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, idempotency_key)
);
