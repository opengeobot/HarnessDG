-- V010__create_config_history_table.sql
-- 功能：创建系统配置变更历史表
-- 时间：2026-05-07
-- 作者：AxeXie

-- 系统配置变更历史表
CREATE TABLE sys_config_history (
    id            BIGSERIAL PRIMARY KEY,
    config_key    VARCHAR(200) NOT NULL,
    old_value     TEXT,
    new_value     TEXT,
    environment   VARCHAR(20) NOT NULL DEFAULT 'all',
    change_type   VARCHAR(20) NOT NULL DEFAULT 'update',
    changed_by    VARCHAR(100) NOT NULL,
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    comment       VARCHAR(500),
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    created_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE sys_config_history IS '系统配置变更历史';
COMMENT ON COLUMN sys_config_history.config_key IS '配置键';
COMMENT ON COLUMN sys_config_history.old_value IS '变更前的值';
COMMENT ON COLUMN sys_config_history.new_value IS '变更后的值';
COMMENT ON COLUMN sys_config_history.environment IS '环境（all/dev/test/prod）';
COMMENT ON COLUMN sys_config_history.change_type IS '变更类型（create/update/delete）';
COMMENT ON COLUMN sys_config_history.changed_by IS '变更操作人';
COMMENT ON COLUMN sys_config_history.changed_at IS '变更时间';
COMMENT ON COLUMN sys_config_history.comment IS '变更备注';

CREATE INDEX idx_config_history_key ON sys_config_history(config_key, changed_at DESC);
CREATE INDEX idx_config_history_env ON sys_config_history(environment);
CREATE INDEX idx_config_history_time ON sys_config_history(changed_at DESC);
