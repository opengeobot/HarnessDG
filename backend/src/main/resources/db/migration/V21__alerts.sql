-- V21: 系统告警历史表
--       记录平台级告警状态（触发/恢复），供管理员查询与审计。

CREATE TABLE IF NOT EXISTS system_alert (
    id              BIGSERIAL       PRIMARY KEY,
    alert_id        VARCHAR(64)     NOT NULL,
    alert_type      VARCHAR(64)     NOT NULL,
    severity        VARCHAR(16)     NOT NULL DEFAULT 'WARNING'
                        CONSTRAINT ck_alert_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    title           VARCHAR(256)    NOT NULL,
    detail          TEXT,
    status          VARCHAR(16)     NOT NULL DEFAULT 'FIRING'
                        CONSTRAINT ck_alert_status CHECK (status IN ('FIRING', 'RESOLVED')),
    source_metric   VARCHAR(128),
    threshold_value DOUBLE PRECISION,
    current_value   DOUBLE PRECISION,
    fired_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ,
    trace_id        VARCHAR(64)
);

COMMENT ON TABLE system_alert IS '系统告警历史表，记录触发与恢复事件';
COMMENT ON COLUMN system_alert.alert_id IS '告警唯一 ID（alt_+ULID）';
COMMENT ON COLUMN system_alert.alert_type IS '告警类型（如 DEAD_TASK, OUTBOX_BACKLOG, AUTH_FAILURE_RATE）';
COMMENT ON COLUMN system_alert.severity IS '严重等级：INFO / WARNING / CRITICAL';
COMMENT ON COLUMN system_alert.status IS '告警状态：FIRING（触发中）/ RESOLVED（已恢复）';

CREATE INDEX IF NOT EXISTS idx_system_alert_type_status ON system_alert (alert_type, status);
CREATE INDEX IF NOT EXISTS idx_system_alert_fired_at ON system_alert (fired_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_system_alert_alert_id ON system_alert (alert_id);
