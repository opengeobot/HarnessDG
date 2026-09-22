-- ============================================================================
-- 功能: P3 发布请求冻结 sourceCommit，支撑内容漂移检测。
-- 时间: 2026-07-10
-- 作者: AxeXie
-- ============================================================================

ALTER TABLE publish_request
    ADD COLUMN IF NOT EXISTS frozen_source_commit VARCHAR(64);

COMMENT ON COLUMN publish_request.frozen_source_commit IS '冻结时的 sourceCommit SHA（40 hex）';
