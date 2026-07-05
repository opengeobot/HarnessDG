-- ============================================================================
-- 功能: P1 资产目录 Schema 补充——Team Owner 外键、别名 JSONB。
--       注：DATASET 多值分类字段（task_codes/modality_codes 等）已由 V14 整改，
--           (namespace, type, name) 唯一约束已由 V2 建立，此处仅补 Team Owner 关联。
-- 时间: 2026-07-06
-- 作者: AxeXie
-- ============================================================================

-- ---- asset 表新增 Team Owner 外键 ----

-- owner_team_id 指向 team.team_id，表示资产的主 Owner 团队。
-- DEC-011: Owner 至少一个 Team，但保持可空以兼容历史数据。
ALTER TABLE asset ADD COLUMN IF NOT EXISTS owner_team_id VARCHAR(32);

-- 外键约束（幂等）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_asset_owner_team'
    ) THEN
        ALTER TABLE asset
            ADD CONSTRAINT fk_asset_owner_team
            FOREIGN KEY (owner_team_id) REFERENCES team (team_id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_asset_owner_team ON asset (owner_team_id) WHERE owner_team_id IS NOT NULL;

COMMENT ON COLUMN asset.owner_team_id IS '主 Owner 团队 ID（FK→team.team_id），DEC-011 要求 Owner 至少一个 Team；可空兼容历史数据';

-- ---- asset 表新增 aliases JSONB ----

-- aliases 存储资产的别名列表（如旧 namespace/name 对），用于重定向和搜索。
-- 与 asset_alias 表互补：asset_alias 记录永久重命名历史，aliases 存储当前有效别名。
ALTER TABLE asset ADD COLUMN IF NOT EXISTS aliases JSONB DEFAULT '[]'::jsonb;

COMMENT ON COLUMN asset.aliases IS '资产当前有效别名列表（JSON 数组），用于重定向和搜索；与 asset_alias 永久历史表互补';

-- ---- asset_dataset 补齐索引 ----

-- sample_count 区间索引（加速大小区间筛选）
CREATE INDEX IF NOT EXISTS ix_asset_dataset_size_bucket
    ON asset_dataset (size_bucket_code) WHERE size_bucket_code IS NOT NULL;

-- 为 asset 表添加 tagIds GIN 索引（通过 asset_tag 表关联）
-- 注：asset_tag 表已有 (asset_id, tag_id) 唯一索引和 asset_id 索引，
--     此处为反向查询（按 tag 查资产）补齐 tag_id 索引。
-- ix_asset_tag_tag 已由 V12 创建，此处不再重复。
