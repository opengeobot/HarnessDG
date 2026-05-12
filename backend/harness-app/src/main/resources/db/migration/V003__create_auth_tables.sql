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

COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.id IS '主键ID';
COMMENT ON COLUMN sys_user.username IS '用户名（唯一）';
COMMENT ON COLUMN sys_user.password_hash IS '密码哈希（BCrypt）';
COMMENT ON COLUMN sys_user.display_name IS '显示名称';
COMMENT ON COLUMN sys_user.email IS '电子邮箱';
COMMENT ON COLUMN sys_user.phone IS '手机号码';
COMMENT ON COLUMN sys_user.avatar_url IS '头像URL';
COMMENT ON COLUMN sys_user.locale IS '用户语言偏好';
COMMENT ON COLUMN sys_user.status IS '状态（active/disabled）';
COMMENT ON COLUMN sys_user.last_login_at IS '最后登录时间';
COMMENT ON COLUMN sys_user.is_deleted IS '逻辑删除标识';
COMMENT ON COLUMN sys_user.created_at IS '创建时间';
COMMENT ON COLUMN sys_user.updated_at IS '最后更新时间';

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

COMMENT ON TABLE sys_role IS '系统角色表';
COMMENT ON COLUMN sys_role.id IS '主键ID';
COMMENT ON COLUMN sys_role.code IS '角色编码（唯一）';
COMMENT ON COLUMN sys_role.name IS '角色名称（多语言JSONB）';
COMMENT ON COLUMN sys_role.description IS '角色描述（多语言JSONB）';
COMMENT ON COLUMN sys_role.is_system IS '是否系统内置（不可删除）';
COMMENT ON COLUMN sys_role.status IS '状态（active/inactive）';
COMMENT ON COLUMN sys_role.created_at IS '创建时间';
COMMENT ON COLUMN sys_role.updated_at IS '最后更新时间';

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

COMMENT ON TABLE sys_user_role IS '用户角色关联表';
COMMENT ON COLUMN sys_user_role.id IS '主键ID';
COMMENT ON COLUMN sys_user_role.user_id IS '用户ID';
COMMENT ON COLUMN sys_user_role.role_id IS '角色ID';
COMMENT ON COLUMN sys_user_role.data_domain IS '数据域（空表示全局）';
COMMENT ON COLUMN sys_user_role.granted_at IS '授权时间';
COMMENT ON COLUMN sys_user_role.granted_by IS '授权人';

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

COMMENT ON TABLE sys_permission IS '角色权限表';
COMMENT ON COLUMN sys_permission.id IS '主键ID';
COMMENT ON COLUMN sys_permission.role_id IS '角色ID';
COMMENT ON COLUMN sys_permission.resource_type IS '资源类型';
COMMENT ON COLUMN sys_permission.resource_id IS '资源ID';
COMMENT ON COLUMN sys_permission.action IS '操作（read/write/delete等）';
COMMENT ON COLUMN sys_permission.effect IS '效果（allow/deny）';
COMMENT ON COLUMN sys_permission.conditions IS '条件表达式（JSONB）';
COMMENT ON COLUMN sys_permission.created_at IS '创建时间';

CREATE INDEX idx_permission_role ON sys_permission(role_id);
