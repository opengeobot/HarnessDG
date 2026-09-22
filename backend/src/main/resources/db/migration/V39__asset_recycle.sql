/*
 * 功能: 资产物理回收字段——archived_at / recycled_at 时间戳。
 * 时间: 2026-07-31
 * 作者: AxeXie
 */

-- ===========================================================================
-- 资产物理回收字段
-- ===========================================================================

ALTER TABLE asset ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE asset ADD COLUMN IF NOT EXISTS recycled_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN asset.archived_at IS '归档时间（归档后开始计算保留期）';
COMMENT ON COLUMN asset.recycled_at IS '物理回收时间（回收后不可恢复）';