-- ============================================================================
-- 功能: P0-B organization 模块迁移——组织作用域、组织项目与组织成员关系。
--       组织/项目作为统一授权模型的授权来源（PRD 5.5）；organization_member 承载成员关系，
--       既用于组织级资源访问隔离（数据库阶段下推），也供 AuthorizationService 的 AccessScope
--       接通组织成员作用域。组织/项目状态为稳定枚举（ACTIVE/DISABLED），成员角色 P0-B 简化为
--       OWNER/MEMBER 字符串。principal_id 不设外键（与 iam_role_binding 一致），由应用层校验存在性。
-- 时间: 2026-06-30
-- 作者: AxeXie
-- ============================================================================

-- 组织表：组织作用域主体，code 全局唯一。
CREATE TABLE IF NOT EXISTS organization (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id     VARCHAR(40)  NOT NULL UNIQUE,
    code                VARCHAR(64)  NOT NULL UNIQUE,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(512),
    gitea_organization  VARCHAR(128),
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(40),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_organization_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE organization IS '组织表，组织作用域主体，承载组织/项目授权来源；code 全局唯一';
COMMENT ON COLUMN organization.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN organization.organization_id IS '业务组织 ID（org_+ULID），跨系统唯一标识';
COMMENT ON COLUMN organization.code IS '组织编码，全局唯一，小写字母/数字/连字符';
COMMENT ON COLUMN organization.name IS '组织名称';
COMMENT ON COLUMN organization.description IS '组织描述（可空）';
COMMENT ON COLUMN organization.gitea_organization IS '关联的 Gitea 组织名（可空，P0-B 仅记录）';
COMMENT ON COLUMN organization.status IS '组织状态：ACTIVE 活跃 / DISABLED 禁用';
COMMENT ON COLUMN organization.created_by IS '创建者主体 ID';
COMMENT ON COLUMN organization.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN organization.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN organization.row_version IS '乐观锁版本号';

-- 项目表：组织下的项目作用域，(organization_id, code) 唯一。
CREATE TABLE IF NOT EXISTS project (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      VARCHAR(40)  NOT NULL UNIQUE,
    organization_id VARCHAR(40)  NOT NULL REFERENCES organization (organization_id) ON DELETE CASCADE,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_project_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ux_project_organization_code UNIQUE (organization_id, code)
);

CREATE INDEX IF NOT EXISTS ix_project_organization ON project (organization_id);

COMMENT ON TABLE project IS '项目表，组织下的项目作用域，(organization_id, code) 唯一';
COMMENT ON COLUMN project.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN project.project_id IS '业务项目 ID（prj_+ULID），跨系统唯一标识';
COMMENT ON COLUMN project.organization_id IS '所属组织 ID，外键引用 organization.organization_id';
COMMENT ON COLUMN project.code IS '项目编码，组织内唯一';
COMMENT ON COLUMN project.name IS '项目名称';
COMMENT ON COLUMN project.description IS '项目描述（可空）';
COMMENT ON COLUMN project.status IS '项目状态：ACTIVE 活跃 / DISABLED 禁用';
COMMENT ON COLUMN project.created_by IS '创建者主体 ID';
COMMENT ON COLUMN project.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN project.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN project.row_version IS '乐观锁版本号';

-- 组织成员表：承载主体与组织的成员关系，(organization_id, principal_id) 唯一。
-- principal_id 不设外键（与 iam_role_binding 一致），由应用层校验主体存在性。
CREATE TABLE IF NOT EXISTS organization_member (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id VARCHAR(40)  NOT NULL REFERENCES organization (organization_id) ON DELETE CASCADE,
    principal_id    VARCHAR(40)  NOT NULL,
    role            VARCHAR(16)  NOT NULL DEFAULT 'MEMBER',
    created_by      VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_organization_member UNIQUE (organization_id, principal_id),
    CONSTRAINT ck_organization_member_role CHECK (role IN ('OWNER', 'MEMBER'))
);

-- 按 organization_id 查成员（列出组织成员）；按 principal_id 查所属组织（成员隔离下推查询）。
CREATE INDEX IF NOT EXISTS ix_organization_member_organization ON organization_member (organization_id);
CREATE INDEX IF NOT EXISTS ix_organization_member_principal ON organization_member (principal_id);

COMMENT ON TABLE organization_member IS '组织成员表，承载主体与组织的成员关系；(organization_id, principal_id) 唯一';
COMMENT ON COLUMN organization_member.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN organization_member.organization_id IS '所属组织 ID，外键引用 organization.organization_id';
COMMENT ON COLUMN organization_member.principal_id IS '成员主体 ID，引用 iam_principal.principal_id（不设外键，应用层校验）';
COMMENT ON COLUMN organization_member.role IS '成员角色：OWNER 所有者 / MEMBER 普通成员（P0-B 简化为字符串）';
COMMENT ON COLUMN organization_member.created_by IS '创建者主体 ID';
COMMENT ON COLUMN organization_member.created_at IS '加入时间（UTC）';
