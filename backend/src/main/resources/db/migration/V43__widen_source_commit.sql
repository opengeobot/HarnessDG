-- 功能: 加宽 source_commit 列以容纳 Noop 物化器的 local-<64hex> 合成提交（70 字符）
-- 时间: 2026-09-23
-- 作者: AxeXie
-- 背景: Gitea 禁用回退（NoopVersionMaterializer / UploadMaterializeJobHandler.resolveSourceCommit）
--       写入 'local-' + manifest_digest（最长 70 字符），超过 V16/V27 的 VARCHAR(64)，
--       导致物化作业在 PG 层失败（value too long）。真实 Gitea 提交 SHA 恒为 40 hex，
--       加宽仅为合成回退值让出空间，与 manifest_digest VARCHAR(128) 保持同型。

ALTER TABLE asset_version
    ALTER COLUMN source_commit TYPE VARCHAR(128);

COMMENT ON COLUMN asset_version.source_commit IS '版本对应的 Gitea Commit SHA（40 hex）；Gitea 禁用时为 local-<manifestDigest> 合成值';

ALTER TABLE publish_request
    ALTER COLUMN frozen_source_commit TYPE VARCHAR(128);

COMMENT ON COLUMN publish_request.frozen_source_commit IS '冻结时的 sourceCommit（真实 40 hex 或 local- 合成值）';
