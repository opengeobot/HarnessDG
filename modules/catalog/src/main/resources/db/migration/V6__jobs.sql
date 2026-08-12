-- V6__jobs.sql
-- 异步 Job 最小投影（04 §5 JobEnvelope / 06 章 workflow 在阶段 5 扩展为完整框架）。
-- 阶段 2 的 DELETE/restore 受理回执使用该表返回 JobEnvelope。

CREATE TABLE jobs (
    id             BIGSERIAL PRIMARY KEY,
    public_id      UUID UNIQUE NOT NULL,
    job_type       VARCHAR(64) NOT NULL,
    status         VARCHAR(24) NOT NULL DEFAULT 'queued'
                   CHECK (status IN ('queued', 'running', 'retry_wait', 'succeeded', 'failed',
                                     'cancel_requested', 'cancelled', 'dead_letter')),
    aggregate_type VARCHAR(64),
    aggregate_id   VARCHAR(128),
    payload        JSONB NOT NULL DEFAULT '{}',
    error_message  TEXT,
    created_by     BIGINT REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_jobs_aggregate ON jobs(aggregate_type, aggregate_id, id DESC);
