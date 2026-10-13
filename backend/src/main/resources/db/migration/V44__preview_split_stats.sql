-- 功能: asset_preview 扩展 split_stats JSONB 列——Split 维度与基础统计投影（TASK-P0BR-078 / DEC-015）
-- 时间: 2026-10-10
-- 作者: AxeXie
-- 背景: REQ-DST-DETAIL-001 要求 Split 与行数/大小/文件数/列数统计绑定精确 Version 并在
--       Preview Tab 呈现；既有 asset_preview（V16）仅有样例 content 投影，无结构化统计列。
--       前向迁移、不回填存量行：NULL = 旧投影，服务层显式投影为「无 Split 维度/统计未知」，
--       不得伪造零值（AC-DST-PRE-002 语义）。回滚 = 不删列（forward-only），旧读路径不受影响。

ALTER TABLE asset_preview ADD COLUMN IF NOT EXISTS split_stats JSONB;

COMMENT ON TABLE asset_preview IS '资产预览缓存：样例内容投影（content）与 Split/统计投影（split_stats），均为 untrustedContent，前端需 DOMPurify';
COMMENT ON COLUMN asset_preview.split_stats IS 'Split 维度与基础统计 JSON 投影：{ splits: [{ name, rowCount, fileCount, sizeBytes, columns, truncated }], totals: { rowCount, fileCount, sizeBytes, truncated }, generatedFrom: { versionId, sourceCommit, generatedAt } }；NULL=旧投影（统计未知，重新生成预览后产出）';
