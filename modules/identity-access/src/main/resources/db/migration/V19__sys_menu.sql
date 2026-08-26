-- V19__sys_menu.sql
-- 标准 RBAC 菜单权限（字典统一+菜单权限重构计划 §四）：菜单树表
-- （目录/菜单/按钮三类），菜单经 permission_code 挂载现有权限点，
-- 用户可见菜单 = 其权限集合过滤结果（不建角色-菜单中间表，授权单一真相源）。

CREATE TABLE sys_menu (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,
    name_zh         VARCHAR(64) NOT NULL,
    name_en         VARCHAR(64) NOT NULL,
    parent_code     VARCHAR(64) REFERENCES sys_menu(code),
    menu_type       VARCHAR(16) NOT NULL CHECK (menu_type IN ('directory', 'menu', 'button')),
    path            VARCHAR(128),
    permission_code VARCHAR(64) REFERENCES sys_permission(code),
    sort_order      INT NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_sys_menu_parent ON sys_menu(parent_code);

-- ---------- 新增权限点：菜单管理与仓库运维（收敛粗粒度 platform_admin 校验） ----------
INSERT INTO sys_permission (code, module, description, built_in) VALUES
('admin:menu:view',   'menu', '查看菜单配置', true),
('admin:menu:manage', 'menu', '管理菜单配置', true),
('admin:repo:view',   'repo', '查看仓库运维列表', true),
('admin:repo:manage', 'repo', '仓库运维操作（重试/强制删除）', true);

-- platform_admin 全量授权（含既有 8 点共 12 点）；platform_auditor 增授仓库只读
DO $$
DECLARE admin_role BIGINT; auditor_role BIGINT;
BEGIN
    SELECT id INTO admin_role FROM sys_role WHERE code = 'platform_admin';
    SELECT id INTO auditor_role FROM sys_role WHERE code = 'platform_auditor';

    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT admin_role, id FROM sys_permission
    WHERE code IN ('admin:menu:view', 'admin:menu:manage', 'admin:repo:view', 'admin:repo:manage');

    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT auditor_role, id FROM sys_permission
    WHERE code = 'admin:repo:view';
END $$;

-- ---------- 种子菜单树：admin_console 目录 + 8 个管理菜单 ----------
INSERT INTO sys_menu (code, name_zh, name_en, parent_code, menu_type, path, permission_code, sort_order) VALUES
('admin_console', '管理后台', 'Admin Console', NULL, 'directory', '/admin', NULL, 0),
('admin_overview', '系统概览', 'Overview', 'admin_console', 'menu', '/admin/overview', 'admin:system:view', 0),
('admin_users',    '用户管理', 'Users',    'admin_console', 'menu', '/admin/users',    'admin:user:view',   1),
('admin_roles',    '角色权限', 'Roles',    'admin_console', 'menu', '/admin/roles',    'admin:role:view',   2),
('admin_dicts',    '字典管理', 'Dicts',    'admin_console', 'menu', '/admin/dicts',    'admin:dict:view',   3),
('admin_audit',    '审计日志', 'Audit',    'admin_console', 'menu', '/admin/audit',    'admin:audit:view',  4),
('admin_repos',    '仓库运维', 'Repos',    'admin_console', 'menu', '/admin/repos',    'admin:repo:view',   5),
('admin_orgs',     '组织管理', 'Orgs',     'admin_console', 'menu', '/admin/orgs',     'admin:system:view', 6),
('admin_menus',    '菜单管理', 'Menus',    'admin_console', 'menu', '/admin/menus',    'admin:menu:view',   7);
