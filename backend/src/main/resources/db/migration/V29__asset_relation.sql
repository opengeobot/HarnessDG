-- ============================================================================
-- 功能: 资产血缘关系表——记录模型/数据集之间的派生、训练、微调等 lineage 边。
-- 依据: PRD §7.1 / §5.2
-- 时间: 2026-07-11
-- 作者: AxeXie
-- ============================================================================

CREATE TABLE IF NOT EXISTS asset_relation (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    relation_id     VARCHAR(40)  NOT NULL UNIQUE,
    parent_asset_id VARCHAR(40)  NOT NULL REFERENCES asset (asset_id),
    child_asset_id  VARCHAR(40)  NOT NULL REFERENCES asset (asset_id),
    relation_type   VARCHAR(32)  NOT NULL,
    created_by      VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_asset_relation_type CHECK (relation_type IN (
        'DERIVED_FROM', 'TRAINED_ON', 'BASED_ON', 'FINE_TUNED_FROM'
    )),
    CONSTRAINT ck_asset_relation_distinct CHECK (parent_asset_id <> child_asset_id),
    CONSTRAINT ux_asset_relation_edge UNIQUE (parent_asset_id, child_asset_id, relation_type)
);

CREATE INDEX IF NOT EXISTS ix_asset_relation_parent ON asset_relation (parent_asset_id);
CREATE INDEX IF NOT EXISTS ix_asset_relation_child ON asset_relation (child_asset_id);

COMMENT ON TABLE asset_relation IS '资产血缘关系边，记录派生/训练/微调等 lineage';
COMMENT ON COLUMN asset_relation.relation_id IS '血缘关系业务 ID（rel_+ULID）';
COMMENT ON COLUMN asset_relation.parent_asset_id IS '上游/父资产 ID';
COMMENT ON COLUMN asset_relation.child_asset_id IS '下游/子资产 ID';
COMMENT ON COLUMN asset_relation.relation_type IS '关系类型：DERIVED_FROM/TRAINED_ON/BASED_ON/FINE_TUNED_FROM';
COMMENT ON COLUMN asset_relation.created_by IS '创建者主体 ID';
COMMENT ON COLUMN asset_relation.created_at IS '创建时间（UTC）';
