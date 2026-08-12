-- V2__catalog.sql
-- 目录域核心表（03 章）：资源类型注册表、taxonomy、仓库、类型 profile、facet 投影、
-- gated 策略/申请/grant、协作者、git 绑定、outbox、统计投影。
-- 索引基线见 03 §9；唯一冲突/状态迁移冲突由应用层映射为 409（03 §10）。

-- ---------- 资源类型注册表（03 §2.2） ----------
CREATE TABLE resource_types (
    type_key                 VARCHAR(64) PRIMARY KEY,
    display_name             VARCHAR(128) NOT NULL,
    current_schema_version   INTEGER,
    capabilities             JSONB NOT NULL DEFAULT '[]',
    handler_key              VARCHAR(64) NOT NULL,
    renderer_key             VARCHAR(64) NOT NULL,
    status                   VARCHAR(16) NOT NULL DEFAULT 'active'
                             CHECK (status IN ('active', 'deprecated', 'disabled')),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE resource_type_schema_versions (
    type_key            VARCHAR(64) NOT NULL REFERENCES resource_types(type_key),
    version             INTEGER NOT NULL,
    metadata_schema     JSONB NOT NULL,
    ui_schema           JSONB NOT NULL DEFAULT '{}',
    facet_definitions   JSONB NOT NULL DEFAULT '[]',
    file_policy         JSONB NOT NULL DEFAULT '{}',
    default_visibility  VARCHAR(16) NOT NULL DEFAULT 'public'
                        CHECK (default_visibility IN ('public', 'organization', 'private')),
    allowed_workflows   JSONB NOT NULL DEFAULT '[]',
    checksum            VARCHAR(64) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'draft'
                        CHECK (status IN ('draft', 'published', 'deprecated')),
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (type_key, version)
);

-- 复合外键：current_schema_version 必须指向已存在的 schema 版本（03 §2.2）
ALTER TABLE resource_types
    ADD CONSTRAINT fk_resource_types_current_schema
    FOREIGN KEY (type_key, current_schema_version)
    REFERENCES resource_type_schema_versions(type_key, version);

-- ---------- 分类与枚举（03 §4） ----------
CREATE TABLE taxonomies (
    id           BIGSERIAL PRIMARY KEY,
    taxonomy_key VARCHAR(64) UNIQUE NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'deprecated')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE taxonomy_values (
    id           BIGSERIAL PRIMARY KEY,
    taxonomy_id  BIGINT NOT NULL REFERENCES taxonomies(id),
    value_key    VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    parent_id    BIGINT REFERENCES taxonomy_values(id),
    sort_order   INT NOT NULL DEFAULT 0,
    status       VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'deprecated')),
    valid_from   TIMESTAMPTZ,
    valid_to     TIMESTAMPTZ,
    aliases      JSONB NOT NULL DEFAULT '[]',
    metadata     JSONB NOT NULL DEFAULT '{}',
    UNIQUE (taxonomy_id, value_key)
);
CREATE INDEX ix_taxonomy_values_parent ON taxonomy_values(parent_id);

-- ---------- gated 策略（03 §7：gated 的唯一持久化真相源） ----------
CREATE TABLE repository_gated_policies (
    id                     BIGSERIAL PRIMARY KEY,
    enabled                BOOLEAN NOT NULL DEFAULT false,
    generation             BIGINT NOT NULL DEFAULT 1,
    default_grant_ttl_seconds BIGINT NOT NULL DEFAULT 2592000,
    request_schema_version INT NOT NULL DEFAULT 1,
    version                BIGINT NOT NULL DEFAULT 0,
    created_by             BIGINT REFERENCES users(id),
    updated_by             BIGINT REFERENCES users(id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- 仓库（03 §2.3 全字段） ----------
CREATE TABLE repositories (
    id                      BIGSERIAL PRIMARY KEY,
    public_id               UUID UNIQUE NOT NULL,
    namespace_id            BIGINT NOT NULL REFERENCES namespaces(id),
    resource_type           VARCHAR(64) NOT NULL REFERENCES resource_types(type_key),
    name                    VARCHAR(128) NOT NULL,
    normalized_name         CITEXT NOT NULL,
    display_name            VARCHAR(128),
    description             TEXT,
    created_by_user_id      BIGINT NOT NULL REFERENCES users(id),
    visibility              VARCHAR(16) NOT NULL DEFAULT 'public'
                            CHECK (visibility IN ('public', 'organization', 'private')),
    gated_policy_id         BIGINT UNIQUE REFERENCES repository_gated_policies(id),
    lifecycle_status        VARCHAR(16) NOT NULL DEFAULT 'provisioning'
                            CHECK (lifecycle_status IN ('provisioning', 'draft', 'active', 'archived',
                                                        'deleting', 'deleted', 'purging', 'purged', 'failed')),
    restore_target_status   VARCHAR(16) CHECK (restore_target_status IN ('active', 'archived')),
    retention_until         TIMESTAMPTZ,
    metadata_schema_version INTEGER NOT NULL,
    metadata                JSONB NOT NULL DEFAULT '{}',
    default_branch          VARCHAR(128) NOT NULL DEFAULT 'main',
    latest_commit_sha       VARCHAR(64),
    provision_retry_count   INT NOT NULL DEFAULT 0,
    provision_next_retry_at TIMESTAMPTZ,
    provision_last_error    TEXT,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT fk_repo_schema_version
        FOREIGN KEY (resource_type, metadata_schema_version)
        REFERENCES resource_type_schema_versions(type_key, version)
);

-- 删除状态通过部分唯一索引处理（03 §2.3）：deleted/purging/purged 释放名称
CREATE UNIQUE INDEX uq_repo_ns_type_name
    ON repositories(namespace_id, resource_type, normalized_name)
    WHERE lifecycle_status NOT IN ('deleted', 'purging', 'purged');

-- 索引基线（03 §9）
CREATE INDEX ix_repos_ns_type_updated
    ON repositories(namespace_id, resource_type, updated_at DESC, id DESC);
CREATE INDEX ix_repos_type_status_vis
    ON repositories(resource_type, lifecycle_status, visibility, updated_at DESC, id DESC);

-- ---------- 类型扩展表（03 §3） ----------
CREATE TABLE model_profiles (
    repository_id          BIGINT PRIMARY KEY REFERENCES repositories(id),
    task_value_id          BIGINT REFERENCES taxonomy_values(id),
    architecture_value_id  BIGINT REFERENCES taxonomy_values(id),
    parameter_count        BIGINT,
    parameter_unit         VARCHAR(16),
    primary_language_value_id BIGINT REFERENCES taxonomy_values(id),
    api_status             VARCHAR(32),
    deployable             BOOLEAN NOT NULL DEFAULT false,
    mcp_compatible         BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE dataset_profiles (
    repository_id     BIGINT PRIMARY KEY REFERENCES repositories(id),
    task_value_id     BIGINT REFERENCES taxonomy_values(id),
    estimated_rows    BIGINT,
    data_formats      JSONB NOT NULL DEFAULT '[]',
    sensitivity_level VARCHAR(32),
    preview_policy    VARCHAR(32)
);

CREATE TABLE studio_profiles (
    repository_id   BIGINT PRIMARY KEY REFERENCES repositories(id),
    publish_status  VARCHAR(16) NOT NULL DEFAULT 'draft'
                    CHECK (publish_status IN ('draft', 'metadata_only', 'building', 'published', 'failed', 'disabled')),
    runtime_type    VARCHAR(32),
    deployable      BOOLEAN NOT NULL DEFAULT false,
    mcp_compatible  BOOLEAN NOT NULL DEFAULT false,
    cover_object_id BIGINT
);

-- 通用 facet 投影（03 §3.4）：服务端按 facet_definitions 生成，客户端不得直写
CREATE TABLE repository_facet_values (
    id             BIGSERIAL PRIMARY KEY,
    repository_id  BIGINT NOT NULL REFERENCES repositories(id),
    type_key       VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL,
    facet_key      VARCHAR(64) NOT NULL,
    value_type     VARCHAR(16) NOT NULL CHECK (value_type IN ('text', 'number', 'boolean')),
    value_text     VARCHAR(256),
    value_number   NUMERIC(38, 10),
    value_boolean  BOOLEAN,
    ordinal        INT NOT NULL DEFAULT 0
);
CREATE INDEX ix_facet_lookup
    ON repository_facet_values(type_key, facet_key, value_text, repository_id);
CREATE INDEX ix_facet_repo ON repository_facet_values(repository_id);

-- ---------- 仓库协作者（02 §2/§3.3：显式、可审计、可过期；subject 可为 user 或 organization） ----------
CREATE TABLE repository_collaborators (
    id                     BIGSERIAL PRIMARY KEY,
    repository_id          BIGINT NOT NULL REFERENCES repositories(id),
    subject_type           VARCHAR(16) NOT NULL CHECK (subject_type IN ('user', 'organization')),
    subject_user_id        BIGINT REFERENCES users(id),
    subject_organization_id BIGINT REFERENCES organizations(id),
    role                   VARCHAR(16) NOT NULL CHECK (role IN ('read', 'write', 'maintain', 'admin')),
    granted_by             BIGINT REFERENCES users(id),
    expires_at             TIMESTAMPTZ,
    version                BIGINT NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_collab_subject CHECK (
        (subject_type = 'user' AND subject_user_id IS NOT NULL AND subject_organization_id IS NULL)
        OR (subject_type = 'organization' AND subject_organization_id IS NOT NULL AND subject_user_id IS NULL))
);
CREATE UNIQUE INDEX uq_collab_user ON repository_collaborators(repository_id, subject_user_id)
    WHERE subject_type = 'user';
CREATE UNIQUE INDEX uq_collab_org ON repository_collaborators(repository_id, subject_organization_id)
    WHERE subject_type = 'organization';
CREATE INDEX ix_collaborator_user ON repository_collaborators(subject_user_id);

-- ---------- gated 申请与 grant（03 §7） ----------
CREATE TABLE gated_access_requests (
    id            BIGSERIAL PRIMARY KEY,
    public_id     UUID UNIQUE NOT NULL,
    repository_id BIGINT NOT NULL REFERENCES repositories(id),
    user_id       BIGINT NOT NULL REFERENCES users(id),
    status        VARCHAR(16) NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending', 'approved', 'rejected', 'revoked', 'expired', 'withdrawn')),
    message       TEXT,
    reviewed_by   BIGINT REFERENCES users(id),
    reviewed_at   TIMESTAMPTZ,
    version       BIGINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_gated_requests_user ON gated_access_requests(user_id);
CREATE INDEX ix_gated_requests_repo ON gated_access_requests(repository_id, status);

CREATE TABLE gated_access_grants (
    id                BIGSERIAL PRIMARY KEY,
    repository_id     BIGINT NOT NULL REFERENCES repositories(id),
    policy_generation BIGINT NOT NULL,
    user_id           BIGINT NOT NULL REFERENCES users(id),
    request_id        BIGINT REFERENCES gated_access_requests(id),
    granted_by        BIGINT REFERENCES users(id),
    valid_from        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    conditions        JSONB NOT NULL DEFAULT '{}',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_gated_grants_user ON gated_access_grants(repository_id, user_id);

-- ---------- Git 绑定（03 §5.1：repositories 不保存 Provider 专属列） ----------
CREATE TABLE repository_git_bindings (
    id                     BIGSERIAL PRIMARY KEY,
    repository_id          BIGINT NOT NULL UNIQUE REFERENCES repositories(id),
    provider               VARCHAR(32) NOT NULL DEFAULT 'gitea',
    external_repository_id VARCHAR(128) NOT NULL,
    external_namespace     VARCHAR(128) NOT NULL,
    external_name          VARCHAR(128) NOT NULL,
    object_format          VARCHAR(16) NOT NULL DEFAULT 'sha1',
    sync_status            VARCHAR(16) NOT NULL DEFAULT 'pending'
                           CHECK (sync_status IN ('pending', 'synced', 'error')),
    last_synced_at         TIMESTAMPTZ,
    last_error             TEXT,
    UNIQUE (provider, external_repository_id)
);

-- ---------- Transactional Outbox（05 §10.1） ----------
CREATE TABLE outbox_events (
    id                BIGSERIAL PRIMARY KEY,
    event_id          UUID UNIQUE NOT NULL,
    event_type        VARCHAR(64) NOT NULL,
    schema_version    INT NOT NULL DEFAULT 1,
    aggregate_id      VARCHAR(128) NOT NULL,
    aggregate_version BIGINT,
    payload           JSONB NOT NULL,
    trace_id          VARCHAR(64),
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at      TIMESTAMPTZ,
    attempts          INT NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error        TEXT
);
CREATE INDEX ix_outbox_unpublished
    ON outbox_events(published_at, created_at) WHERE published_at IS NULL;

-- ---------- 统计投影（03 §6.2：强事实表可重建来源） ----------
CREATE TABLE repository_stats (
    repository_id BIGINT PRIMARY KEY REFERENCES repositories(id),
    likes         BIGINT NOT NULL DEFAULT 0,
    favorites     BIGINT NOT NULL DEFAULT 0,
    downloads     BIGINT NOT NULL DEFAULT 0,
    visits        BIGINT NOT NULL DEFAULT 0,
    file_count    BIGINT NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
