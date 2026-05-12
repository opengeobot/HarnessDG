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

COMMENT ON TABLE sys_config IS '系统配置表';
COMMENT ON COLUMN sys_config.id IS '主键ID';
COMMENT ON COLUMN sys_config.config_key IS '配置键（环境内唯一）';
COMMENT ON COLUMN sys_config.config_value IS '配置值';
COMMENT ON COLUMN sys_config.value_type IS '值类型（string/number/boolean/json）';
COMMENT ON COLUMN sys_config.category IS '分类';
COMMENT ON COLUMN sys_config.description IS '描述（多语言JSONB）';
COMMENT ON COLUMN sys_config.is_encrypted IS '是否加密存储';
COMMENT ON COLUMN sys_config.is_readonly IS '是否只读（不可修改）';
COMMENT ON COLUMN sys_config.environment IS '环境（all/dev/test/prod）';
COMMENT ON COLUMN sys_config.is_deleted IS '逻辑删除标识';
COMMENT ON COLUMN sys_config.created_by IS '创建人';
COMMENT ON COLUMN sys_config.updated_by IS '最后更新人';
COMMENT ON COLUMN sys_config.created_at IS '创建时间';
COMMENT ON COLUMN sys_config.updated_at IS '最后更新时间';

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

COMMENT ON TABLE sys_audit_log IS '审计日志表';
COMMENT ON COLUMN sys_audit_log.id IS '主键ID';
COMMENT ON COLUMN sys_audit_log.trace_id IS '追踪ID（全链路关联）';
COMMENT ON COLUMN sys_audit_log.task_id IS '关联任务ID';
COMMENT ON COLUMN sys_audit_log.operator IS '操作人';
COMMENT ON COLUMN sys_audit_log.action IS '操作类型';
COMMENT ON COLUMN sys_audit_log.resource_type IS '资源类型';
COMMENT ON COLUMN sys_audit_log.resource_id IS '资源ID';
COMMENT ON COLUMN sys_audit_log.resource_name IS '资源名称';
COMMENT ON COLUMN sys_audit_log.detail IS '操作详情（JSONB）';
COMMENT ON COLUMN sys_audit_log.agent_session_id IS 'Agent会话ID';
COMMENT ON COLUMN sys_audit_log.ip_address IS '操作人IP地址';
COMMENT ON COLUMN sys_audit_log.user_agent IS '用户代理（浏览器信息）';
COMMENT ON COLUMN sys_audit_log.status IS '操作状态（success/failure）';
COMMENT ON COLUMN sys_audit_log.duration_ms IS '操作耗时（毫秒）';
COMMENT ON COLUMN sys_audit_log.created_at IS '创建时间';

CREATE INDEX idx_audit_trace ON sys_audit_log(trace_id);
CREATE INDEX idx_audit_task ON sys_audit_log(task_id) WHERE task_id IS NOT NULL;
CREATE INDEX idx_audit_operator_time ON sys_audit_log(operator, created_at DESC);
CREATE INDEX idx_audit_resource ON sys_audit_log(resource_type, resource_id);
