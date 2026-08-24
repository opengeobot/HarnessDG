-- V15__ddl_alignment.sql
-- 功能：DDL 与 03 章数据模型对齐（object_blobs 去重键扩 size_bytes、file_versions 唯一键扩 commit_sha）
-- 作者：AxeXie
-- 日期：2026-08-24
-- DDL 与 03 章数据模型对齐（03 §5.4/§5.5）：
-- 1) object_blobs 去重键纳入 size_bytes：同 sha 不同大小不共享 blob（03 §5.4
--    UNIQUE(tenant_scope_id, sha256, size_bytes)）。V7 内联 UNIQUE (namespace_id, sha256)
--    由 PG 自动命名为 object_blobs_namespace_id_sha256_key，IF EXISTS 兼容两种命名。
-- 2) file_versions 头版本唯一键纳入 commit_sha（03 §5.5
--    UNIQUE(repository_id, branch_name, path, commit_sha)）。v1 简化决策：保留
--    staging/active 部分索引语义（历史 deleted 仍可多版本引用），仅扩展键列，
--    保证同一提交内同路径至多一个头版本，跨提交覆盖仍由旧版本转 deleted 保证唯一。

-- 03 §5.4: 去重键纳入 size_bytes（同 sha 不同大小不共享 blob）
ALTER TABLE object_blobs DROP CONSTRAINT IF EXISTS object_blobs_namespace_id_sha256_key;
ALTER TABLE object_blobs ADD CONSTRAINT uq_object_blobs_dedup UNIQUE (namespace_id, sha256, size_bytes);

-- 03 §5.5: 头版本唯一键纳入 commit_sha（v1 保留 staging/active 部分索引语义作为简化决策，见迁移注释）
DROP INDEX IF EXISTS uq_file_version_head;
CREATE UNIQUE INDEX uq_file_version_head ON file_versions(repository_id, branch, path, commit_sha) WHERE status IN ('staging','active');
