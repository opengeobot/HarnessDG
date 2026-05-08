-- V005__create_config_audit_tables.sql
-- 功能：创建系统配置表和审计日志表
-- 时间：2026-05-07
-- 作者：AxeXie

-- 系统配置表
CREATE TABLE sys_config (
    id            BIGSERIAL PRIMARY KEY,
    config_key    VARCHAR(200) NOT NULL,
    config_value  TEXT,
    value_type    VARCHAR(20) NOT NULL DEFAULT 'string',
    category      VARCHAR(50) NOT NULL,
    description   JSONB,
    is_encrypted  BOOLEAN NOT NULL DEFAULT FALSE,
    is_readonly   BOOLEAN NOT NULL DEFAULT FALSE,
    environment   VARCHAR(20) NOT NULL DEFAULT 'all',
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    created_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_config_key_env ON sys_config(config_key, environment) WHERE is_deleted = FALSE;

-- 审计日志表
CREATE TABLE sys_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(100) NOT NULL,
    task_id         BIGINT,
    operator        VARCHAR(100) NOT NULL,
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(50) NOT NULL,
    resource_id     VARCHAR(100),
    resource_name   VARCHAR(255),
    detail          JSONB,
    agent_session_id VARCHAR(255),
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'success',
    duration_ms     BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_trace ON sys_audit_log(trace_id);
CREATE INDEX idx_audit_task ON sys_audit_log(task_id) WHERE task_id IS NOT NULL;
CREATE INDEX idx_audit_operator_time ON sys_audit_log(operator, created_at DESC);
CREATE INDEX idx_audit_resource ON sys_audit_log(resource_type, resource_id);
