-- ============================================================================
-- 功能: P0-B audit 迁移——追加写审计日志表。审计不可篡改：应用层无 UPDATE/DELETE 路径，
--       并通过触发器在数据库层阻止 UPDATE/DELETE（普通管理员不可改/删审计）。
--       必审计事件覆盖登录/Token/Agent/角色/ACL/组织成员/字典/标签/配置/被拒高风险等。
--       request_summary 字段级脱敏（Token/JWT/密码/预签名串/凭据/Cookie）。
-- 时间: 2026-06-30
-- 作者: AxeXie
-- ============================================================================

CREATE TABLE IF NOT EXISTS audit_log (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id         VARCHAR(40)  NOT NULL UNIQUE,
    event_type       VARCHAR(64)  NOT NULL,
    principal_id     VARCHAR(40),
    principal_type   VARCHAR(16),
    action           VARCHAR(64)  NOT NULL,
    resource_type    VARCHAR(32),
    resource_id      VARCHAR(40),
    scope_type       VARCHAR(16),
    scope_id         VARCHAR(40),
    result           VARCHAR(16)  NOT NULL DEFAULT 'SUCCEEDED',
    error_code       VARCHAR(64),
    request_summary  JSONB,
    trace_id         VARCHAR(64),
    request_id       VARCHAR(64),
    duration_ms      BIGINT,
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version      INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_audit_log_result CHECK (result IN ('SUCCEEDED', 'FAILED', 'DENIED'))
);

COMMENT ON TABLE audit_log IS '追加写审计日志表，不可篡改；触发器阻止 UPDATE/DELETE';
COMMENT ON COLUMN audit_log.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN audit_log.audit_id IS '审计记录业务 ID（aud_+ULID），全局唯一';
COMMENT ON COLUMN audit_log.event_type IS '审计事件类型（如 ROLE_CREATED、AUTH_PERMISSION_DENIED）';
COMMENT ON COLUMN audit_log.principal_id IS '操作者主体 ID';
COMMENT ON COLUMN audit_log.principal_type IS '操作者主体类型（USER/AGENT/SYSTEM）';
COMMENT ON COLUMN audit_log.action IS '动作（与事件类型对齐，便于按动作检索）';
COMMENT ON COLUMN audit_log.resource_type IS '目标资源类型（如 ROLE、ASSET）';
COMMENT ON COLUMN audit_log.resource_id IS '目标资源业务 ID';
COMMENT ON COLUMN audit_log.scope_type IS '作用域类型（PLATFORM/ORGANIZATION/PROJECT）';
COMMENT ON COLUMN audit_log.scope_id IS '作用域 ID';
COMMENT ON COLUMN audit_log.result IS '结果：SUCCEEDED/FAILED/DENIED';
COMMENT ON COLUMN audit_log.error_code IS '失败/拒绝时的错误码';
COMMENT ON COLUMN audit_log.request_summary IS '已脱敏的请求摘要（JSONB，字段级脱敏）';
COMMENT ON COLUMN audit_log.trace_id IS '分布式追踪 ID';
COMMENT ON COLUMN audit_log.request_id IS '请求 ID';
COMMENT ON COLUMN audit_log.duration_ms IS '操作耗时（毫秒，可空）';
COMMENT ON COLUMN audit_log.occurred_at IS '事件发生时间（UTC）';
COMMENT ON COLUMN audit_log.row_version IS '乐观锁版本号（保留列）';

-- 游标分页索引：按发生时间 + 主键键集分页。
CREATE INDEX IF NOT EXISTS idx_audit_log_cursor ON audit_log (occurred_at, id);
CREATE INDEX IF NOT EXISTS idx_audit_log_principal ON audit_log (principal_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_resource ON audit_log (resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_event ON audit_log (event_type);

-- ----------------------------------------------------------------------------
-- 不可篡改触发器：阻止对审计记录的 UPDATE/DELETE。
-- 应用层已无更新路径；触发器作为数据库层兜底，确保普通管理员无法改/删审计。
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: UPDATE/DELETE is not allowed';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_log_no_update ON audit_log;
CREATE TRIGGER trg_audit_log_no_update BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log_immutable();

DROP TRIGGER IF EXISTS trg_audit_log_no_delete ON audit_log;
CREATE TRIGGER trg_audit_log_no_delete BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log_immutable();
