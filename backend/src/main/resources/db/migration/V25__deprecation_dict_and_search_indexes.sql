-- V25: 弃用原因字典种子 + asset_dataset JSONB GIN 索引 + 搜索性能索引
-- 功能: P1 缺口修复——弃用原因字典校验、搜索性能优化
-- 时间: 2026-07-08

-- ============================================================
-- 1. 弃用原因字典种子
-- ============================================================
INSERT INTO system_dict_type (dict_code, dict_name, description, status, created_at, updated_at)
SELECT 'deprecation_reason', '弃用原因', '资产弃用时必须选择的原因分类', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE dict_code = 'deprecation_reason');

INSERT INTO system_dict_item (dict_code, item_code, item_name, description, sort_order, status, created_at, updated_at)
SELECT 'deprecation_reason', 'REPLACED', '已被替代', '该资产已被其他资产替代', 1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_dict_item WHERE dict_code = 'deprecation_reason' AND item_code = 'REPLACED');

INSERT INTO system_dict_item (dict_code, item_code, item_name, description, sort_order, status, created_at, updated_at)
SELECT 'deprecation_reason', 'OUTDATED', '已过时', '该资产内容已过时不再适用', 2, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_dict_item WHERE dict_code = 'deprecation_reason' AND item_code = 'OUTDATED');

INSERT INTO system_dict_item (dict_code, item_code, item_name, description, sort_order, status, created_at, updated_at)
SELECT 'deprecation_reason', 'SECURITY_ISSUE', '安全问题', '该资产存在已知安全风险', 3, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_dict_item WHERE dict_code = 'deprecation_reason' AND item_code = 'SECURITY_ISSUE');

INSERT INTO system_dict_item (dict_code, item_code, item_name, description, sort_order, status, created_at, updated_at)
SELECT 'deprecation_reason', 'UNSUPPORTED', '不再维护', '该资产已停止维护支持', 4, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_dict_item WHERE dict_code = 'deprecation_reason' AND item_code = 'UNSUPPORTED');

INSERT INTO system_dict_item (dict_code, item_code, item_name, description, sort_order, status, created_at, updated_at)
SELECT 'deprecation_reason', 'MERGED', '已合并', '该资产已合并到其他资产', 5, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_dict_item WHERE dict_code = 'deprecation_reason' AND item_code = 'MERGED');

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
