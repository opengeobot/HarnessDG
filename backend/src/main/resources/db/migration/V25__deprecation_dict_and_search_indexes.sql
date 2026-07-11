-- V25: 弃用原因字典种子 + asset_dataset JSONB GIN 索引 + 搜索性能索引
-- 功能: P1 缺口修复——弃用原因字典校验、搜索性能优化
-- 时间: 2026-07-08

-- ============================================================
-- 1. 弃用原因字典项种子（类型已由 V6 预置）
-- ============================================================
INSERT INTO system_dict_item (dict_item_id, dict_code, item_code, i18n_key, sort_order)
SELECT 'dct_' || dict_code || '_' || item_code,
       dict_code,
       item_code,
       'dict.' || dict_code || '.' || item_code,
       sort_order
FROM (VALUES
    ('deprecation_reason', 'REPLACED',       1),
    ('deprecation_reason', 'OUTDATED',       2),
    ('deprecation_reason', 'SECURITY_ISSUE', 3),
    ('deprecation_reason', 'UNSUPPORTED',    4),
    ('deprecation_reason', 'MERGED',         5)
) AS seed(dict_code, item_code, sort_order)
ON CONFLICT (dict_code, item_code) DO NOTHING;

-- ============================================================
-- 2. asset_dataset JSONB 数组 GIN 索引（加速多值分类过滤）
-- ============================================================
CREATE INDEX IF NOT EXISTS ix_asset_dataset_task_codes
    ON asset_dataset USING gin (task_codes)
    WHERE task_codes IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_asset_dataset_modality_codes
    ON asset_dataset USING gin (modality_codes)
    WHERE modality_codes IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_asset_dataset_format_codes
    ON asset_dataset USING gin (format_codes)
    WHERE format_codes IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_asset_dataset_language_codes
    ON asset_dataset USING gin (language_codes)
    WHERE language_codes IS NOT NULL;

-- ============================================================
-- 3. 搜索性能复合索引
-- ============================================================
CREATE INDEX IF NOT EXISTS ix_asset_model_framework_task
    ON asset_model (framework, task)
    WHERE framework IS NOT NULL AND task IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_asset_dataset_format_modality
    ON asset_dataset (format, modality)
    WHERE format IS NOT NULL AND modality IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_asset_completed_keyset
    ON asset (status, created_at DESC, id DESC)
    WHERE deleted = 0 AND provisioning_status = 'COMPLETED';

CREATE INDEX IF NOT EXISTS ix_version_artifact_version_artifact
    ON version_artifact (version_id, artifact_id);
