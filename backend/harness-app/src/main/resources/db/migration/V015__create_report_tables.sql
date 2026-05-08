-- V015__create_report_tables.sql
-- 功能：创建周报表
-- 时间：2026-05-08
-- 作者：AxeXie

-- 周报表
CREATE TABLE rpt_weekly_report (
    id                  BIGSERIAL PRIMARY KEY,
    report_type         VARCHAR(50) NOT NULL DEFAULT 'weekly',    -- weekly/monthly/special
    title               VARCHAR(255) NOT NULL,                    -- 报告标题
    time_range_start    TIMESTAMPTZ NOT NULL,                     -- 时间范围起始
    time_range_end      TIMESTAMPTZ NOT NULL,                     -- 时间范围结束
    content_json        JSONB NOT NULL,                           -- 结构化报告内容
    markdown_content    TEXT,                                     -- 渲染后 Markdown
    metrics_snapshot    JSONB,                                    -- 引用的指标快照
    generated_by        VARCHAR(100) NOT NULL,                    -- 生成人 user_id/system
    agent_session_id    VARCHAR(255),                             -- Agent 会话 ID
    status              VARCHAR(20) NOT NULL DEFAULT 'generating',  -- generating/ready/failed
    error_message       TEXT,                                     -- 错误信息
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE rpt_weekly_report IS '周报表';
COMMENT ON COLUMN rpt_weekly_report.report_type IS '报告类型：weekly/monthly/special';
COMMENT ON COLUMN rpt_weekly_report.content_json IS '结构化报告内容（JSONB）';
COMMENT ON COLUMN rpt_weekly_report.markdown_content IS '渲染后的 Markdown 报告';
COMMENT ON COLUMN rpt_weekly_report.metrics_snapshot IS '引用的指标快照（JSONB）';
COMMENT ON COLUMN rpt_weekly_report.agent_session_id IS 'Agent 会话 ID，用于追踪';

CREATE INDEX idx_report_type ON rpt_weekly_report(report_type) WHERE is_deleted = FALSE;
CREATE INDEX idx_report_status ON rpt_weekly_report(status) WHERE is_deleted = FALSE;
CREATE INDEX idx_report_generated_by ON rpt_weekly_report(generated_by) WHERE is_deleted = FALSE;
CREATE INDEX idx_report_time_range ON rpt_weekly_report(time_range_start, time_range_end) WHERE is_deleted = FALSE;
