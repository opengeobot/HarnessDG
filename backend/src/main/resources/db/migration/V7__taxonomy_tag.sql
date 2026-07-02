-- ============================================================================
-- 功能: P0-B taxonomy 受控标签迁移——平台/组织作用域的受控标签与资产-标签关联表。
--       标签是治理资源，资产写接口只接受已登记的 tagId，拒绝自由标签。停用标签保留历史关联
--       与回显，但不可用于新建关联。(scope_type, scope_id, tag_code) 唯一；平台标签 scope_id
--       归一化为固定值 'PLATFORM' 以参与唯一约束。asset_tag 本迁移仅建表结构，资产侧写入逻辑
--       由 Task 14 资产整改实现。
-- 时间: 2026-06-30
-- 作者: AxeXie
-- ============================================================================

-- 受控标签表：平台/组织作用域的治理标签；tag_id 稳定标识，(scope_type, scope_id, tag_code) 唯一。
CREATE TABLE IF NOT EXISTS system_tag (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tag_id          VARCHAR(40)  NOT NULL UNIQUE,
    scope_type      VARCHAR(16)  NOT NULL,
    scope_id        VARCHAR(40)  NOT NULL DEFAULT 'PLATFORM',
    tag_code        VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    i18n_key        VARCHAR(128) NOT NULL,
    color           VARCHAR(32),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 1,
    row_version     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_system_tag_scope_type CHECK (scope_type IN ('PLATFORM', 'ORGANIZATION')),
    CONSTRAINT ck_system_tag_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    -- 平台标签 scope_id 必须为固定值 'PLATFORM'；组织标签 scope_id 必须为具体组织 ID（非 'PLATFORM'）。
    CONSTRAINT ck_system_tag_scope_id CHECK (
        (scope_type = 'PLATFORM' AND scope_id = 'PLATFORM')
        OR (scope_type = 'ORGANIZATION' AND scope_id <> 'PLATFORM')
    ),
    CONSTRAINT ux_system_tag UNIQUE (scope_type, scope_id, tag_code)
);

CREATE INDEX IF NOT EXISTS ix_system_tag_scope ON system_tag (scope_type, scope_id);

COMMENT ON TABLE system_tag IS '受控标签表，平台/组织作用域的治理标签；(scope_type, scope_id, tag_code) 唯一，资产只引用已登记 tagId';
COMMENT ON COLUMN system_tag.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN system_tag.tag_id IS '业务标签 ID（tag_+ULID），跨系统稳定标识，资产写接口据此引用';
COMMENT ON COLUMN system_tag.scope_type IS '作用域类型：PLATFORM 平台 / ORGANIZATION 组织';
COMMENT ON COLUMN system_tag.scope_id IS '作用域 ID：平台标签固定为 PLATFORM；组织标签为 organization_id';
COMMENT ON COLUMN system_tag.tag_code IS '标签编码（作用域内稳定值）';
COMMENT ON COLUMN system_tag.name IS '标签展示名（管理后台兜底展示，正式展示按 i18n_key）';
COMMENT ON COLUMN system_tag.i18n_key IS '国际化键，前端据此取展示文案';
COMMENT ON COLUMN system_tag.color IS '标签颜色（可空，如 #1677ff）';
COMMENT ON COLUMN system_tag.status IS '状态：ACTIVE 启用（可新建关联）/ DISABLED 停用（保留历史关联，不可新建）';
COMMENT ON COLUMN system_tag.created_by IS '创建者主体 ID';
COMMENT ON COLUMN system_tag.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN system_tag.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN system_tag.version IS '语义版本号，新增为 1，每次更新自增；对外暴露并用于 expectedVersion 乐观并发校验';
COMMENT ON COLUMN system_tag.row_version IS '乐观锁版本号（保留列）';

-- 资产-标签关联表：承载资产与受控标签的多对多关联，(asset_id, tag_id) 唯一。
-- 本迁移仅建表结构；资产创建/更新时按 tagIds 写入由 Task 14 资产整改实现。
CREATE TABLE IF NOT EXISTS asset_tag (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id        VARCHAR(40)  NOT NULL,
    tag_id          VARCHAR(40)  NOT NULL REFERENCES system_tag (tag_id) ON DELETE RESTRICT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_asset_tag UNIQUE (asset_id, tag_id)
);

CREATE INDEX IF NOT EXISTS ix_asset_tag_asset ON asset_tag (asset_id);
CREATE INDEX IF NOT EXISTS ix_asset_tag_tag ON asset_tag (tag_id);

COMMENT ON TABLE asset_tag IS '资产-标签关联表，承载资产与受控标签的多对多关联；(asset_id, tag_id) 唯一';
COMMENT ON COLUMN asset_tag.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN asset_tag.asset_id IS '资产业务 ID（ast_+ULID），不设外键由应用层校验';
COMMENT ON COLUMN asset_tag.tag_id IS '标签业务 ID，外键引用 system_tag.tag_id（停用标签保留历史关联，故 RESTRICT）';
COMMENT ON COLUMN asset_tag.created_at IS '关联建立时间（UTC）';
