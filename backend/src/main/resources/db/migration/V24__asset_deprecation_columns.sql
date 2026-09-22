-- ============================================================================
-- 功能: P1 资产目录弃用 Schema——deprecation_reason / deprecation_note / replacement_asset_id。
-- 时间: 2026-07-08
-- 作者: AxeXie
-- ============================================================================

ALTER TABLE asset ADD COLUMN IF NOT EXISTS deprecation_reason VARCHAR(64);
ALTER TABLE asset ADD COLUMN IF NOT EXISTS deprecation_note TEXT;
ALTER TABLE asset ADD COLUMN IF NOT EXISTS replacement_asset_id VARCHAR(40);

COMMENT ON COLUMN asset.deprecation_reason IS '弃用原因编码（如 REPLACED/OUTDATED/SECURITY_ISSUE/UNSUPPORTED）';
COMMENT ON COLUMN asset.deprecation_note IS '弃用说明（自由文本）';
COMMENT ON COLUMN asset.replacement_asset_id IS '替代资产 ID（可空，指向同表另一资产）';

-- 替代资产外键（幂等）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_asset_replacement'
    ) THEN
        ALTER TABLE asset
            ADD CONSTRAINT fk_asset_replacement
            FOREIGN KEY (replacement_asset_id) REFERENCES asset (asset_id) ON DELETE SET NULL;
    END IF;
END $$;

-- Facet 维度索引（partial WHERE deleted=0）
CREATE INDEX IF NOT EXISTS ix_asset_facet_type ON asset (type) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS ix_asset_facet_license ON asset (license) WHERE deleted = 0 AND license IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_asset_model_framework ON asset_model (framework) WHERE framework IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_asset_model_task ON asset_model (task) WHERE task IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_asset_dataset_format ON asset_dataset (format) WHERE format IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_asset_dataset_modality ON asset_dataset (modality) WHERE modality IS NOT NULL;
