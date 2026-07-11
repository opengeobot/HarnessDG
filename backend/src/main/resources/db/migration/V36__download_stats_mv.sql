-- =============================================================================
-- 功能: 下载统计物化视图——预聚合 DOWNLOAD_TICKET_ISSUED 审计事件，
--       替代 audit_log JOIN asset_version 的实时聚合查询。
-- 时间: 2026-07-11
-- =============================================================================

CREATE MATERIALIZED VIEW mv_download_stats AS
SELECT
    av.asset_id,
    av.version_id,
    av.version,
    COUNT(*) AS download_count
FROM audit_log al
JOIN asset_version av ON al.resource_id = av.version_id
WHERE al.event_type = 'DOWNLOAD_TICKET_ISSUED'
  AND al.result = 'SUCCEEDED'
GROUP BY av.asset_id, av.version_id, av.version;

CREATE UNIQUE INDEX idx_mv_download_stats_version
    ON mv_download_stats (version_id);

CREATE INDEX idx_mv_download_stats_asset
    ON mv_download_stats (asset_id);
