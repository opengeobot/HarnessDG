-- V16__rbac.sql
-- RBAC 权限模型（管理后台计划 §一）：角色/权限/角色-权限/用户-角色四表。
-- 种子内置 platform_admin（全部权限）与 platform_auditor（只读管理面）；
-- 旧 platform_role_assignments 活跃记录迁移入 sys_user_role 后，旧表保留只读。

CREATE TABLE sys_role (
    id          BIGSERIAL PRIMARY KEY,
    public_id   UUID UNIQUE NOT NULL,
    code        CITEXT UNIQUE NOT NULL,
    built_in    BOOLEAN NOT NULL DEFAULT false,
    description VARCHAR(512),
    status      VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sys_permission (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(64) UNIQUE NOT NULL,
    module      VARCHAR(32) NOT NULL,
    description VARCHAR(256),
    built_in    BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL REFERENCES sys_role(id),
    permission_id BIGINT NOT NULL REFERENCES sys_permission(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE sys_user_role (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    role_id    BIGINT NOT NULL REFERENCES sys_role(id),
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    revoked_by BIGINT REFERENCES users(id)
);
CREATE INDEX ix_sys_user_role_user ON sys_user_role(user_id);
CREATE INDEX ix_sys_user_role_role ON sys_user_role(role_id);
-- 部分唯一索引：同一用户对同一角色仅允许一条活跃指派
CREATE UNIQUE INDEX uq_sys_user_role_active ON sys_user_role(user_id, role_id) WHERE revoked_at IS NULL;

-- ---------- 内置权限点（管理面词表） ----------
INSERT INTO sys_permission (code, module, description, built_in) VALUES
('admin:system:view', 'system', '查看系统概览', true),
('admin:audit:view',  'audit',  '查看审计日志', true),
('admin:user:view',   'user',   '查看用户列表', true),
('admin:user:manage', 'user',   '管理用户（禁用/启用/解锁）', true),
('admin:role:view',   'role',   '查看角色与权限', true),
('admin:role:manage', 'role',   '管理角色与指派', true),
('admin:dict:view',   'dict',   '查看字典', true),
('admin:dict:manage', 'dict',   '管理字典', true);

-- ---------- 内置角色与授权 ----------
DO $$
DECLARE admin_role BIGINT; auditor_role BIGINT;
BEGIN
    INSERT INTO sys_role (public_id, code, built_in, description)
    VALUES (gen_random_uuid(), 'platform_admin', true, '平台管理员：平台配置、用户/组织治理、审计与所有资源应急管理')
    RETURNING id INTO admin_role;
    INSERT INTO sys_role (public_id, code, built_in, description)
    VALUES (gen_random_uuid(), 'platform_auditor', true, '平台审计员：只读查看审计与运行状态，不获得资源写权限')
    RETURNING id INTO auditor_role;

    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT admin_role, id FROM sys_permission;

    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT auditor_role, id FROM sys_permission
    WHERE code IN ('admin:system:view', 'admin:audit:view', 'admin:user:view');
END $$;

-- ---------- 存量数据迁移：活跃平台角色指派 → sys_user_role ----------
INSERT INTO sys_user_role (user_id, role_id, created_by, created_at)
SELECT pra.user_id, r.id, pra.granted_by, pra.granted_at
FROM platform_role_assignments pra
JOIN sys_role r ON r.code = pra.role
WHERE pra.revoked_at IS NULL;
