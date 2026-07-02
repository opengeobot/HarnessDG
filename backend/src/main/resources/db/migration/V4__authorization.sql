-- ============================================================================
-- 功能: P0-B authorization 模块迁移——统一权限模型 RBAC + 角色绑定 + 资源 ACL + Agent 工具白名单。
--       承载权限定义清单（resource:action）、平台/组织角色、角色-权限关联、主体作用域角色绑定、
--       资源级显式 ACL 以及 Agent 的 MCP Tool 授权关联，作为统一 AuthorizationService 的事实源。
--       预置全部权限定义与基础角色（ADMIN/READER/ASSET_AUTHOR），首个管理员的 ADMIN 绑定由
--       运行时引导器补齐（迁移无法预知运行期生成的 principal_id）。
-- 时间: 2026-06-30
-- 作者: AxeXie
-- ============================================================================

-- 权限定义表：权限以 resource:action 编码，是 RBAC 的最小授权单元。
CREATE TABLE IF NOT EXISTS iam_permission (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    permission_id   VARCHAR(40)  NOT NULL UNIQUE,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    resource        VARCHAR(64)  NOT NULL,
    action          VARCHAR(64)  NOT NULL,
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE iam_permission IS '权限定义表，登记 resource:action 形式的最小授权单元，供角色聚合引用';
COMMENT ON COLUMN iam_permission.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN iam_permission.permission_id IS '业务权限 ID（prm_+ULID 或预置稳定键），跨系统标识';
COMMENT ON COLUMN iam_permission.code IS '权限编码（resource:action），全局唯一';
COMMENT ON COLUMN iam_permission.resource IS '资源域（code 冒号前段）';
COMMENT ON COLUMN iam_permission.action IS '操作（code 冒号后段）';
COMMENT ON COLUMN iam_permission.description IS '权限说明';
COMMENT ON COLUMN iam_permission.created_at IS '创建时间（UTC）';

-- 角色表：平台或组织作用域的权限聚合；builtin 角色不可改名/删除。
CREATE TABLE IF NOT EXISTS iam_role (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id         VARCHAR(40)  NOT NULL UNIQUE,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    scope_type      VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM',
    scope_id        VARCHAR(40),
    builtin         SMALLINT     NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_iam_role_scope_type CHECK (scope_type IN ('PLATFORM', 'ORGANIZATION')),
    CONSTRAINT ck_iam_role_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE iam_role IS '角色表，平台/组织作用域的权限聚合；内置角色由系统预置且不可改名删除';
COMMENT ON COLUMN iam_role.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN iam_role.role_id IS '业务角色 ID（rol_+ULID 或预置稳定键），跨系统标识';
COMMENT ON COLUMN iam_role.code IS '角色编码（如 ADMIN/READER/ASSET_AUTHOR），全局唯一';
COMMENT ON COLUMN iam_role.name IS '角色名称';
COMMENT ON COLUMN iam_role.description IS '角色说明';
COMMENT ON COLUMN iam_role.scope_type IS '角色作用域类型：PLATFORM 平台 / ORGANIZATION 组织';
COMMENT ON COLUMN iam_role.scope_id IS '组织角色的组织 ID，平台角色为空';
COMMENT ON COLUMN iam_role.builtin IS '是否内置角色：0 自定义 / 1 内置（不可改名删除）';
COMMENT ON COLUMN iam_role.status IS '角色状态：ACTIVE 活跃 / DISABLED 禁用';
COMMENT ON COLUMN iam_role.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN iam_role.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN iam_role.row_version IS '乐观锁版本号';

-- 角色-权限关联表：多对多，唯一约束防重复授权。
CREATE TABLE IF NOT EXISTS iam_role_permission (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id         VARCHAR(40)  NOT NULL REFERENCES iam_role (role_id) ON DELETE CASCADE,
    permission_id   VARCHAR(40)  NOT NULL REFERENCES iam_permission (permission_id) ON DELETE CASCADE,
    CONSTRAINT ux_iam_role_permission UNIQUE (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS ix_iam_role_permission_role ON iam_role_permission (role_id);

COMMENT ON TABLE iam_role_permission IS '角色-权限关联表，承载角色聚合的权限集合（多对多）';
COMMENT ON COLUMN iam_role_permission.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN iam_role_permission.role_id IS '角色 ID，外键引用 iam_role.role_id';
COMMENT ON COLUMN iam_role_permission.permission_id IS '权限 ID，外键引用 iam_permission.permission_id';

-- 角色绑定表：将角色授予主体并限定作用域（平台/组织/项目）。
CREATE TABLE IF NOT EXISTS iam_role_binding (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    binding_id      VARCHAR(40)  NOT NULL UNIQUE,
    principal_id    VARCHAR(40)  NOT NULL,
    role_id         VARCHAR(40)  NOT NULL REFERENCES iam_role (role_id) ON DELETE CASCADE,
    scope_type      VARCHAR(16)  NOT NULL,
    scope_id        VARCHAR(40),
    created_by      VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_iam_role_binding_scope_type CHECK (scope_type IN ('PLATFORM', 'ORGANIZATION', 'PROJECT'))
);

-- 唯一绑定：同一主体+角色+作用域不可重复；scope_id 为空时以空串归一参与唯一性判断。
CREATE UNIQUE INDEX IF NOT EXISTS ux_iam_role_binding
    ON iam_role_binding (principal_id, role_id, scope_type, COALESCE(scope_id, ''));
CREATE INDEX IF NOT EXISTS ix_iam_role_binding_principal ON iam_role_binding (principal_id);

COMMENT ON TABLE iam_role_binding IS '角色绑定表，将角色授予主体并限定作用域（平台/组织/项目）';
COMMENT ON COLUMN iam_role_binding.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN iam_role_binding.binding_id IS '业务绑定 ID（rbd_+ULID），跨系统标识';
COMMENT ON COLUMN iam_role_binding.principal_id IS '被授权主体 ID，引用 iam_principal.principal_id';
COMMENT ON COLUMN iam_role_binding.role_id IS '角色 ID，外键引用 iam_role.role_id';
COMMENT ON COLUMN iam_role_binding.scope_type IS '绑定作用域类型：PLATFORM/ORGANIZATION/PROJECT';
COMMENT ON COLUMN iam_role_binding.scope_id IS '作用域 ID：组织/项目 ID，平台作用域为空';
COMMENT ON COLUMN iam_role_binding.created_by IS '创建者主体 ID';
COMMENT ON COLUMN iam_role_binding.created_at IS '创建时间（UTC）';

-- 资源 ACL 表：资源级显式授权，按 (资源类型,资源ID,主体,权限) 直接授予。
-- 同一 (资源类型,资源ID,主体) 的多条权限共享同一 acl_id 形成一组（对应契约 ResourceAclView 的 permissionCodes 列表）。
CREATE TABLE IF NOT EXISTS iam_resource_acl (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    acl_id          VARCHAR(40)  NOT NULL,
    resource_type   VARCHAR(32)  NOT NULL,
    resource_id     VARCHAR(64)  NOT NULL,
    principal_id    VARCHAR(40)  NOT NULL,
    permission      VARCHAR(64)  NOT NULL,
    created_by      VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_iam_resource_acl UNIQUE (resource_type, resource_id, principal_id, permission)
);

CREATE INDEX IF NOT EXISTS ix_iam_resource_acl_lookup
    ON iam_resource_acl (resource_type, resource_id, principal_id);
CREATE INDEX IF NOT EXISTS ix_iam_resource_acl_id ON iam_resource_acl (acl_id);

COMMENT ON TABLE iam_resource_acl IS '资源 ACL 表，资源级显式授权，承载 (资源类型,资源ID,主体,权限) 的直接授予；同一资源+主体的多条权限共享 acl_id 成组';
COMMENT ON COLUMN iam_resource_acl.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN iam_resource_acl.acl_id IS '业务 ACL ID（acl_+ULID），同一资源+主体的多条权限共享该 ID';
COMMENT ON COLUMN iam_resource_acl.resource_type IS '资源类型（如 ASSET）';
COMMENT ON COLUMN iam_resource_acl.resource_id IS '资源业务 ID';
COMMENT ON COLUMN iam_resource_acl.principal_id IS '被授权主体 ID';
COMMENT ON COLUMN iam_resource_acl.permission IS '授予的权限编码（resource:action）';
COMMENT ON COLUMN iam_resource_acl.created_by IS '创建者主体 ID';
COMMENT ON COLUMN iam_resource_acl.created_at IS '创建时间（UTC）';

-- Agent 工具授权关联表：以本表为 Agent MCP Tool 授权的权威来源（iam_agent.tool_allowlist 仅保留兼容，不再双写）。
CREATE TABLE IF NOT EXISTS iam_agent_tool (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agent_id        VARCHAR(40)  NOT NULL,
    tool_code       VARCHAR(64)  NOT NULL,
    enabled         SMALLINT     NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_iam_agent_tool UNIQUE (agent_id, tool_code)
);

CREATE INDEX IF NOT EXISTS ix_iam_agent_tool_agent ON iam_agent_tool (agent_id);

COMMENT ON TABLE iam_agent_tool IS 'Agent MCP Tool 授权关联表，作为 Agent 工具白名单的权威来源（取代 iam_agent.tool_allowlist 双写）';
COMMENT ON COLUMN iam_agent_tool.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN iam_agent_tool.agent_id IS 'Agent 业务 ID，引用 iam_agent.agent_id';
COMMENT ON COLUMN iam_agent_tool.tool_code IS 'MCP Tool 编码';
COMMENT ON COLUMN iam_agent_tool.enabled IS '是否启用该工具：0 停用 / 1 启用';
COMMENT ON COLUMN iam_agent_tool.created_at IS '创建时间（UTC）';

-- ----------------------------------------------------------------------------
-- 预置权限定义清单（resource:action）。permission_id 采用稳定派生键，便于幂等重放。
-- ----------------------------------------------------------------------------
INSERT INTO iam_permission (permission_id, code, resource, action, description)
SELECT 'prm_' || replace(code, ':', '_'),
       code,
       split_part(code, ':', 1),
       split_part(code, ':', 2),
       descr
FROM (VALUES
    ('organization:manage', '管理组织'),
    ('project:view',        '查看项目'),
    ('project:manage',      '管理项目'),
    ('user:read',           '查看用户/主体'),
    ('user:manage',         '管理用户'),
    ('authorization:read',  '查看授权（角色/绑定/ACL）'),
    ('authorization:manage','管理授权（角色/绑定/ACL）'),
    ('dictionary:read',     '查看字典'),
    ('dictionary:manage',   '管理字典'),
    ('tag:read',            '查看标签'),
    ('tag:manage',          '管理标签'),
    ('asset:create',        '创建资产'),
    ('asset:read',          '查看资产'),
    ('asset:update',        '更新资产'),
    ('asset:upload',        '上传资产内容'),
    ('asset:download',      '下载资产内容'),
    ('asset:submit',        '提交资产版本'),
    ('asset:review',        '评审资产版本'),
    ('asset:publish',       '发布资产版本（高风险）'),
    ('asset:deprecate',     '弃用资产'),
    ('asset:delete',        '删除资产（高风险）'),
    ('agent:register',      '注册 Agent'),
    ('agent:authorize',     '授权 Agent'),
    ('token:create',        '签发令牌/凭据'),
    ('audit:read',          '查看审计'),
    ('job:read',            '查看任务'),
    ('job:manage',          '管理任务'),
    ('notification:read',   '查看通知'),
    ('system:configure',    '系统配置（高风险）'),
    ('system:observe',      '系统观测'),
    ('mcp:invoke',          '调用 MCP 工具')
) AS seed(code, descr)
ON CONFLICT (code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 预置基础角色（内置，不可改名删除）。role_id 采用稳定键便于关联与引导。
-- ----------------------------------------------------------------------------
INSERT INTO iam_role (role_id, code, name, description, scope_type, builtin, status)
VALUES
    ('rol_admin',        'ADMIN',        '平台管理员', '拥有全部权限的平台管理员', 'PLATFORM', 1, 'ACTIVE'),
    ('rol_reader',       'READER',       '只读用户',   '只读访问平台资源',         'PLATFORM', 1, 'ACTIVE'),
    ('rol_asset_author', 'ASSET_AUTHOR', '资产作者',   '资产创作、更新与提交',     'PLATFORM', 1, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- ADMIN 绑定全部权限。
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_admin', permission_id FROM iam_permission
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- READER 绑定只读类权限。
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_reader', permission_id FROM iam_permission
WHERE code IN ('project:view', 'user:read', 'authorization:read', 'dictionary:read',
               'tag:read', 'asset:read', 'asset:download', 'audit:read', 'job:read',
               'notification:read', 'system:observe')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ASSET_AUTHOR 绑定资产创作类权限。
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_asset_author', permission_id FROM iam_permission
WHERE code IN ('project:view', 'dictionary:read', 'tag:read',
               'asset:create', 'asset:read', 'asset:update', 'asset:upload',
               'asset:download', 'asset:submit', 'asset:deprecate')
ON CONFLICT (role_id, permission_id) DO NOTHING;
