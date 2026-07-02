-- ============================================================================
-- 功能: P0-B identity 模块迁移——统一访问主体、本地用户、Agent 身份与刷新令牌/PAT 摘要。
--       承载本地用户口令哈希、Token 版本、账户锁定、Agent 凭据摘要与 MCP Tool 白名单、
--       以及刷新 JWT 的 jti/Token Family 生命周期，用于禁用即时失效与刷新重放检测。
-- 时间: 2026-06-30
-- 作者: AxeXie
-- ============================================================================

-- 统一访问主体表：所有访问者（用户/Agent/服务等）统一抽象为 Principal。
CREATE TABLE IF NOT EXISTS iam_principal (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    principal_id    VARCHAR(40)  NOT NULL UNIQUE,
    principal_type  VARCHAR(16)  NOT NULL,
    display_name    VARCHAR(128),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_iam_principal_type CHECK (principal_type IN ('USER', 'AGENT', 'SERVICE', 'API_CLIENT', 'WORKER')),
    CONSTRAINT ck_iam_principal_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE iam_principal IS '统一访问主体表，登记用户/Agent/服务等所有访问者的主体身份';
COMMENT ON COLUMN iam_principal.id IS '内部自增主键，仅库内关联使用，不作跨系统标识';
COMMENT ON COLUMN iam_principal.principal_id IS '业务主体 ID（prn_+ULID），跨系统唯一标识';
COMMENT ON COLUMN iam_principal.principal_type IS '主体类型：USER/AGENT/SERVICE/API_CLIENT/WORKER';
COMMENT ON COLUMN iam_principal.display_name IS '主体展示名称';
COMMENT ON COLUMN iam_principal.status IS '主体状态：ACTIVE 活跃 / DISABLED 禁用';
COMMENT ON COLUMN iam_principal.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN iam_principal.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN iam_principal.row_version IS '乐观锁版本号';

-- 本地用户表：PostgreSQL 本地账户，P0 身份事实源；只存口令哈希，绝不存明文。
CREATE TABLE IF NOT EXISTS iam_user (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id               VARCHAR(40)  NOT NULL UNIQUE,
    principal_id          VARCHAR(40)  NOT NULL UNIQUE REFERENCES iam_principal (principal_id),
    username              VARCHAR(64)  NOT NULL UNIQUE,
    display_name          VARCHAR(128) NOT NULL,
    email                 VARCHAR(255),
    locale                VARCHAR(32)  NOT NULL DEFAULT 'zh-CN',
    password_hash         VARCHAR(255) NOT NULL,
    password_algorithm    VARCHAR(32)  NOT NULL DEFAULT 'BCRYPT',
    scopes                JSONB        NOT NULL DEFAULT '[]'::jsonb,
    token_version         BIGINT       NOT NULL DEFAULT 0,
    must_change_password  SMALLINT     NOT NULL DEFAULT 0,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version           INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_iam_user_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'LOCKED', 'DISABLED'))
);

COMMENT ON TABLE iam_user IS '本地用户表，PostgreSQL 本地账户（P0 身份事实源），承载口令哈希、Token 版本与账户锁定状态';
COMMENT ON COLUMN iam_user.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN iam_user.user_id IS '业务用户 ID（usr_+ULID），跨系统唯一标识';
COMMENT ON COLUMN iam_user.principal_id IS '关联主体 ID，外键引用 iam_principal.principal_id';
COMMENT ON COLUMN iam_user.username IS '登录用户名，全局唯一';
COMMENT ON COLUMN iam_user.display_name IS '用户展示名称';
COMMENT ON COLUMN iam_user.email IS '电子邮箱（可空）';
COMMENT ON COLUMN iam_user.locale IS '语言偏好';
COMMENT ON COLUMN iam_user.password_hash IS '口令不可逆哈希摘要，绝不存明文';
COMMENT ON COLUMN iam_user.password_algorithm IS '口令哈希算法标识（如 BCRYPT）';
COMMENT ON COLUMN iam_user.scopes IS '用户粗粒度 Scope 列表（P0-B 简化授权来源，Task 5 角色体系落地后由授权服务覆盖），JSON 数组';
COMMENT ON COLUMN iam_user.token_version IS '主体 Token 版本，改密/禁用后递增以使旧 Token 失效';
COMMENT ON COLUMN iam_user.must_change_password IS '是否必须下次登录修改口令：0 否 / 1 是';
COMMENT ON COLUMN iam_user.failed_login_attempts IS '连续登录失败次数，达到阈值触发锁定';
COMMENT ON COLUMN iam_user.locked_until IS '账户锁定截止时间（UTC），为空表示未锁定';
COMMENT ON COLUMN iam_user.last_login_at IS '最近一次登录成功时间（UTC）';
COMMENT ON COLUMN iam_user.status IS '用户状态：PENDING_ACTIVATION/ACTIVE/LOCKED/DISABLED';
COMMENT ON COLUMN iam_user.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN iam_user.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN iam_user.row_version IS '乐观锁版本号';

-- Agent 主体表：Agent 身份与长期凭据摘要、敏感等级与 MCP Tool 白名单。
CREATE TABLE IF NOT EXISTS iam_agent (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_id              VARCHAR(40)  NOT NULL UNIQUE,
    principal_id          VARCHAR(40)  NOT NULL UNIQUE REFERENCES iam_principal (principal_id),
    display_name          VARCHAR(128) NOT NULL,
    agent_type            VARCHAR(64)  NOT NULL,
    vendor                VARCHAR(64),
    credential_hash       VARCHAR(255) NOT NULL,
    credential_algorithm  VARCHAR(32)  NOT NULL DEFAULT 'BCRYPT',
    max_sensitivity_level INTEGER      NOT NULL DEFAULT 0,
    scopes                JSONB        NOT NULL DEFAULT '[]'::jsonb,
    tool_allowlist        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    token_version         BIGINT       NOT NULL DEFAULT 0,
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version           INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_iam_agent_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE iam_agent IS 'Agent 主体表，承载 Agent 身份、长期凭据摘要、敏感等级、粗粒度 Scope 与 MCP Tool 白名单';
COMMENT ON COLUMN iam_agent.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN iam_agent.agent_id IS '业务 Agent ID（agt_+ULID），跨系统唯一标识';
COMMENT ON COLUMN iam_agent.principal_id IS '关联主体 ID，外键引用 iam_principal.principal_id';
COMMENT ON COLUMN iam_agent.display_name IS 'Agent 展示名称';
COMMENT ON COLUMN iam_agent.agent_type IS 'Agent 类型（如 openclaw/qwenpaw）';
COMMENT ON COLUMN iam_agent.vendor IS 'Agent 供应商（可空）';
COMMENT ON COLUMN iam_agent.credential_hash IS 'Agent 长期凭据不可逆哈希摘要，绝不存明文';
COMMENT ON COLUMN iam_agent.credential_algorithm IS '凭据哈希算法标识（如 BCRYPT）';
COMMENT ON COLUMN iam_agent.max_sensitivity_level IS 'Agent 可访问的最高敏感等级';
COMMENT ON COLUMN iam_agent.scopes IS 'Agent 粗粒度 Scope 列表，JSON 数组';
COMMENT ON COLUMN iam_agent.tool_allowlist IS 'Agent 允许调用的 MCP Tool 白名单，JSON 数组';
COMMENT ON COLUMN iam_agent.token_version IS '主体 Token 版本，禁用后递增以使旧 Token 失效';
COMMENT ON COLUMN iam_agent.status IS 'Agent 状态：ACTIVE 活跃 / DISABLED 禁用';
COMMENT ON COLUMN iam_agent.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN iam_agent.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN iam_agent.row_version IS '乐观锁版本号';

-- 令牌摘要表：刷新 JWT / PAT 的 jti、Token Family 与生命周期，仅存摘要不存完整 Token。
CREATE TABLE IF NOT EXISTS iam_token (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_id        VARCHAR(40)  NOT NULL UNIQUE,
    jti             VARCHAR(64)  NOT NULL UNIQUE,
    token_family    VARCHAR(64)  NOT NULL,
    principal_id    VARCHAR(40)  NOT NULL REFERENCES iam_principal (principal_id),
    token_type      VARCHAR(16)  NOT NULL,
    scopes          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    last_used_at    TIMESTAMPTZ,
    replaced_by_jti VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_iam_token_type CHECK (token_type IN ('ACCESS', 'REFRESH')),
    CONSTRAINT ck_iam_token_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED'))
);

-- 按 Token Family 批量吊销（登出/重放）与按主体清理使用。
CREATE INDEX IF NOT EXISTS ix_iam_token_family ON iam_token (token_family);
CREATE INDEX IF NOT EXISTS ix_iam_token_principal ON iam_token (principal_id);

COMMENT ON TABLE iam_token IS '令牌摘要表，记录刷新 JWT/PAT 的 jti、Token Family 与生命周期状态，仅存摘要不存完整 Token';
COMMENT ON COLUMN iam_token.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN iam_token.token_id IS '业务令牌记录 ID（tok_+ULID）';
COMMENT ON COLUMN iam_token.jti IS 'JWT 唯一标识（jti），用于吊销与重放检测，全局唯一';
COMMENT ON COLUMN iam_token.token_family IS 'Token Family 标识，刷新轮换链与重放整族吊销使用';
COMMENT ON COLUMN iam_token.principal_id IS '所属主体 ID，外键引用 iam_principal.principal_id';
COMMENT ON COLUMN iam_token.token_type IS '令牌类型：ACCESS / REFRESH';
COMMENT ON COLUMN iam_token.scopes IS '令牌粗粒度 Scope 列表，JSON 数组';
COMMENT ON COLUMN iam_token.status IS '令牌状态：ACTIVE 有效 / ROTATED 已轮换 / REVOKED 已吊销 / EXPIRED 已过期';
COMMENT ON COLUMN iam_token.issued_at IS '签发时间（UTC）';
COMMENT ON COLUMN iam_token.expires_at IS '过期时间（UTC）';
COMMENT ON COLUMN iam_token.last_used_at IS '最近使用时间（UTC）';
COMMENT ON COLUMN iam_token.replaced_by_jti IS '轮换后继任令牌的 jti（用于重放追溯）';
COMMENT ON COLUMN iam_token.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN iam_token.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN iam_token.row_version IS '乐观锁版本号';
