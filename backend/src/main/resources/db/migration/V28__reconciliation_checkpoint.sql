-- ============================================================================
-- 功能: P5 对账检查点表——记录各 Reconciler 最近运行游标与统计。
-- 时间: 2026-07-10
-- 作者: AxeXie
-- ============================================================================

CREATE TABLE IF NOT EXISTS reconciliation_checkpoint (
    reconciler_type  VARCHAR(64)  PRIMARY KEY,
    last_cursor      VARCHAR(64),
    last_run_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    total_checked    BIGINT       NOT NULL DEFAULT 0,
    discrepancies    BIGINT       NOT NULL DEFAULT 0
);

COMMENT ON TABLE reconciliation_checkpoint IS '对账 Worker 检查点，记录最近运行游标与累计统计';
COMMENT ON COLUMN reconciliation_checkpoint.reconciler_type IS '对账类型（如 PUBLISHED_VERSION_RECONCILE）';
COMMENT ON COLUMN reconciliation_checkpoint.last_cursor IS '最近一批游标（通常为 jobId 或 version_id/asset_id）';
COMMENT ON COLUMN reconciliation_checkpoint.last_run_at IS '最近运行时间';
COMMENT ON COLUMN reconciliation_checkpoint.total_checked IS '累计检查条目数';
COMMENT ON COLUMN reconciliation_checkpoint.discrepancies IS '累计差异数';
