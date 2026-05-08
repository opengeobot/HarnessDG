-- V003__create_auth_tables.sql
-- 功能：创建用户认证与权限表
-- 时间：2026-05-07
-- 作者：AxeXie

-- 用户表
CREATE TABLE sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    email           VARCHAR(200),
    phone           VARCHAR(20),
    avatar_url      VARCHAR(500),
    locale          VARCHAR(10) NOT NULL DEFAULT 'zh_CN',
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    last_login_at   TIMESTAMPTZ,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_user_username ON sys_user(username) WHERE is_deleted = FALSE;
CREATE UNIQUE INDEX uk_user_email ON sys_user(email) WHERE email IS NOT NULL AND is_deleted = FALSE;

-- 角色表
CREATE TABLE sys_role (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50) NOT NULL,
    name        JSONB NOT NULL,
    description JSONB,
    is_system   BOOLEAN NOT NULL DEFAULT FALSE,
    status      VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_role_code ON sys_role(code);

-- 用户-角色关联表
CREATE TABLE sys_user_role (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES sys_user(id),
    role_id     BIGINT NOT NULL REFERENCES sys_role(id),
    data_domain VARCHAR(100),
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by  VARCHAR(100) NOT NULL
);

CREATE UNIQUE INDEX uk_user_role ON sys_user_role(user_id, role_id, data_domain);

-- 权限表
CREATE TABLE sys_permission (
    id            BIGSERIAL PRIMARY KEY,
    role_id       BIGINT NOT NULL REFERENCES sys_role(id),
    resource_type VARCHAR(50) NOT NULL,
    resource_id   VARCHAR(100),
    action        VARCHAR(50) NOT NULL,
    effect        VARCHAR(10) NOT NULL DEFAULT 'allow',
    conditions    JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_permission_role ON sys_permission(role_id);
