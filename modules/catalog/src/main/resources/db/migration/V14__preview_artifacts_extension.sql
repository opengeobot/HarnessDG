-- V14__preview_artifacts_extension.sql
-- 功能：预览产物契约扩展（source_commit_sha / manifest_hash / status / row_count 等）
-- 作者：AxeXie
-- 日期：2026-08-24
-- 预览产物契约字段（05 §5 / 04 Preview schema）：版本键 repositoryId + refName + version，
-- source_commit_sha / manifest_hash / policy_version 绑定不可变源版本；
-- status: pending/running/ready/failed/stale/unsupported；sample 为不可变采样快照（JSONB）。

ALTER TABLE preview_artifacts ADD COLUMN IF NOT EXISTS source_commit_sha VARCHAR(64);
ALTER TABLE preview_artifacts ADD COLUMN IF NOT EXISTS manifest_hash VARCHAR(64);
ALTER TABLE preview_artifacts ADD COLUMN IF NOT EXISTS policy_version INT NOT NULL DEFAULT 1;
ALTER TABLE preview_artifacts ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'pending';
ALTER TABLE preview_artifacts ADD COLUMN IF NOT EXISTS row_count BIGINT;
ALTER TABLE preview_artifacts ADD COLUMN IF NOT EXISTS sample_count BIGINT;
ALTER TABLE preview_artifacts ADD COLUMN IF NOT EXISTS sample_strategy VARCHAR(64);
ALTER TABLE preview_artifacts ADD COLUMN IF NOT EXISTS sample JSONB;
ALTER TABLE preview_artifacts ADD COLUMN IF NOT EXISTS ref_name VARCHAR(128);
ALTER TABLE preview_artifacts ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS ix_preview_artifacts_repo_ref
    ON preview_artifacts(repository_id, ref_name, version DESC);
