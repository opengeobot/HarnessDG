-- ============================================================================
-- 功能: P1 资产目录领域迁移——创建统一资产表及模型/数据集扩展表。
--       承载模型/数据集的登记、卡片、标签、Owner、可见性与软删除/乐观锁。
-- 时间: 2026-06-29
-- 作者: AxeXie
-- ============================================================================

-- 统一资产表：模型与数据集共用的目录主体。
CREATE TABLE IF NOT EXISTS asset (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id        VARCHAR(40)  NOT NULL UNIQUE,
    type            VARCHAR(16)  NOT NULL,
    namespace       VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    display_name    VARCHAR(255),
    description     TEXT,
    visibility      VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    owners          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    tags            JSONB        NOT NULL DEFAULT '[]'::jsonb,
    license         VARCHAR(64),
    repo_full_name  VARCHAR(255),
    repo_html_url   VARCHAR(512),
    repo_clone_url  VARCHAR(512),
    row_version     INTEGER      NOT NULL DEFAULT 0,
    created_by      VARCHAR(40),
    updated_by      VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT ck_asset_type CHECK (type IN ('MODEL', 'DATASET')),
    CONSTRAINT ck_asset_visibility CHECK (visibility IN ('PRIVATE', 'INTERNAL', 'PUBLIC')),
    CONSTRAINT ck_asset_status CHECK (status IN ('ACTIVE', 'DEPRECATED', 'ARCHIVED'))
);

-- 资产坐标唯一性：同一 namespace+type+name 唯一，软删除记录不参与约束以便重建。
CREATE UNIQUE INDEX IF NOT EXISTS ux_asset_coordinate
    ON asset (namespace, type, name)
    WHERE deleted = 0;

-- 检索常用过滤组合索引。
CREATE INDEX IF NOT EXISTS ix_asset_type_status_visibility
    ON asset (type, status, visibility)
    WHERE deleted = 0;

-- 游标分页按 (created_at, id) 倒序键集翻页。
CREATE INDEX IF NOT EXISTS ix_asset_keyset
    ON asset (created_at DESC, id DESC)
    WHERE deleted = 0;

-- 标签包含查询使用 GIN 索引。
CREATE INDEX IF NOT EXISTS ix_asset_tags ON asset USING gin (tags);

COMMENT ON TABLE asset IS '统一资产表，登记模型与数据集的目录主体（卡片、标签、Owner、可见性等）';
COMMENT ON COLUMN asset.id IS '内部自增主键，仅库内关联使用，不作跨系统标识';
COMMENT ON COLUMN asset.asset_id IS '业务资产 ID（ast_+ULID），跨系统唯一标识';
COMMENT ON COLUMN asset.type IS '资产类型：MODEL 模型 / DATASET 数据集';
COMMENT ON COLUMN asset.namespace IS '命名空间，用于人类可读别名与仓库归属';
COMMENT ON COLUMN asset.name IS '资产名称，namespace+type+name 唯一';
COMMENT ON COLUMN asset.display_name IS '展示名称';
COMMENT ON COLUMN asset.description IS '资产描述（卡片摘要，用于全文检索）';
COMMENT ON COLUMN asset.visibility IS '可见性：PRIVATE 私有 / INTERNAL 内部 / PUBLIC 公开';
COMMENT ON COLUMN asset.status IS '资产状态：ACTIVE 活跃 / DEPRECATED 弃用 / ARCHIVED 归档';
COMMENT ON COLUMN asset.owners IS 'Owner 列表（团队/用户编码），JSON 数组';
COMMENT ON COLUMN asset.tags IS '标签列表，JSON 数组，用于筛选与检索';
COMMENT ON COLUMN asset.license IS '许可证编码（治理字典 itemCode）';
COMMENT ON COLUMN asset.repo_full_name IS 'Gitea 仓库全名（owner/repo）';
COMMENT ON COLUMN asset.repo_html_url IS 'Gitea 仓库 Web 地址';
COMMENT ON COLUMN asset.repo_clone_url IS 'Gitea 仓库 Git 克隆地址';
COMMENT ON COLUMN asset.row_version IS '乐观锁版本号';
COMMENT ON COLUMN asset.created_by IS '创建者主体 ID';
COMMENT ON COLUMN asset.updated_by IS '最后更新者主体 ID';
COMMENT ON COLUMN asset.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN asset.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN asset.deleted IS '逻辑删除标记：0 未删除 / 1 已删除';

-- 模型扩展表：模型类资产的领域字段。
CREATE TABLE IF NOT EXISTS asset_model (
    asset_id     VARCHAR(40)  PRIMARY KEY REFERENCES asset (asset_id),
    framework    VARCHAR(64),
    task         VARCHAR(64),
    architecture VARCHAR(128)
);

COMMENT ON TABLE asset_model IS '模型扩展表，承载模型类资产的框架、任务、架构等领域字段';
COMMENT ON COLUMN asset_model.asset_id IS '业务资产 ID，外键关联 asset.asset_id';
COMMENT ON COLUMN asset_model.framework IS '训练/推理框架（字典 itemCode，如 pytorch）';
COMMENT ON COLUMN asset_model.task IS '模型任务类型（字典 itemCode，如 text-generation）';
COMMENT ON COLUMN asset_model.architecture IS '模型架构描述';

-- 数据集扩展表：数据集类资产的领域字段。
CREATE TABLE IF NOT EXISTS asset_dataset (
    asset_id VARCHAR(40) PRIMARY KEY REFERENCES asset (asset_id),
    format   VARCHAR(64),
    modality VARCHAR(64)
);

COMMENT ON TABLE asset_dataset IS '数据集扩展表，承载数据集类资产的格式、模态等领域字段';
COMMENT ON COLUMN asset_dataset.asset_id IS '业务资产 ID，外键关联 asset.asset_id';
COMMENT ON COLUMN asset_dataset.format IS '数据格式（字典 itemCode，如 parquet）';
COMMENT ON COLUMN asset_dataset.modality IS '数据模态（字典 itemCode，如 text/image）';
