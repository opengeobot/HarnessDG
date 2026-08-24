-- V12__jobs_query_surface.sql
-- 功能：Jobs 查询面完整字段扩展（进度、结果摘要、错误码、起止时间）
-- 作者：AxeXie
-- 日期：2026-08-24
-- Jobs 查询面完整字段（05 §1 Job schema）：进度/结果摘要/错误码/起止时间。

ALTER TABLE jobs ADD COLUMN IF NOT EXISTS progress_current BIGINT;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS progress_total BIGINT;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS progress_message TEXT;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS result_summary JSONB;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS error_code VARCHAR(64);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS finished_at TIMESTAMPTZ;
