-- ============================================================================
-- 功能: 版本/工件唯一性约束——Git Tag 与工件路径去重。
-- 依据: PRD §7.2
-- 时间: 2026-07-11
-- 作者: AxeXie
-- ============================================================================

-- 同一资产下 git_tag 非空时唯一（发布 Tag 不可重复）。
CREATE UNIQUE INDEX IF NOT EXISTS ux_asset_version_git_tag
    ON asset_version (asset_id, git_tag)
    WHERE git_tag IS NOT NULL;

-- 同一版本内工件路径唯一。
ALTER TABLE version_artifact
    ADD CONSTRAINT ux_version_artifact_path UNIQUE (version_id, path);
