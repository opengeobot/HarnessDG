-- V004__create_task_tables.sql
-- 功能：创建业务任务实例表
-- 时间：2026-05-07
-- 作者：AxeXie

CREATE TABLE biz_task (
    id              BIGSERIAL PRIMARY KEY,
    task_code       VARCHAR(100) NOT NULL,
    task_type       VARCHAR(50) NOT NULL,
    title           JSONB NOT NULL,
    description     JSONB,
    status          VARCHAR(30) NOT NULL DEFAULT 'draft',
    priority        VARCHAR(20) NOT NULL DEFAULT 'medium',
    input_params    JSONB,
    execution_plan  JSONB,
    result          JSONB,
    initiator       VARCHAR(100) NOT NULL,
    assignee        VARCHAR(100),
    data_domain     VARCHAR(100),
    entity_id       BIGINT,
    metric_id       BIGINT,
    dagster_run_id  VARCHAR(255),
    agent_session_id VARCHAR(255),
    trace_id        VARCHAR(100),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(100) NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_task_code ON biz_task(task_code) WHERE is_deleted = FALSE;
CREATE INDEX idx_task_type_status ON biz_task(task_type, status) WHERE is_deleted = FALSE;
CREATE INDEX idx_task_initiator ON biz_task(initiator, created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_task_trace ON biz_task(trace_id) WHERE trace_id IS NOT NULL;
