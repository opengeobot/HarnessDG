-- ============================================================================
-- 功能: P0-B job/idempotency 迁移——持久化幂等记录、可靠任务与任务尝试。
--       可靠任务（DVC/发布/Webhook/对账）必须持久化，禁止裸线程/@Async 承担；
--       Worker 以 FOR UPDATE SKIP LOCKED 领取，指数退避+抖动重试，超阈值进入 DEAD。
--       全链路 traceId/principalId/assetId 透传。状态值与 OpenAPI JobStatus 对齐。
-- 时间: 2026-06-30
-- 作者: AxeXie
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 幂等记录表：写接口首次结果落库，命中复用。idempotency_key 全局唯一。
-- request_digest 用于检测同键不同请求体的冲突；response_payload 承载已脱敏的首次响应。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_idempotency (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key  VARCHAR(128) NOT NULL UNIQUE,
    method           VARCHAR(16)  NOT NULL,
    path             VARCHAR(512) NOT NULL,
    request_digest   VARCHAR(128),
    status           VARCHAR(16)  NOT NULL DEFAULT 'COMPLETED',
    response_payload JSONB,
    expires_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,
    CONSTRAINT ck_api_idempotency_status CHECK (status IN ('COMPLETED', 'IN_PROGRESS', 'FAILED'))
);

COMMENT ON TABLE api_idempotency IS '写接口幂等记录表，持久化首次执行结果以供命中复用，保证恰好一次语义';
COMMENT ON COLUMN api_idempotency.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN api_idempotency.idempotency_key IS '客户端幂等键，全局唯一（INSERT ON CONFLICT 处理并发重复）';
COMMENT ON COLUMN api_idempotency.method IS 'HTTP 方法，界定幂等作用域';
COMMENT ON COLUMN api_idempotency.path IS '请求路径，界定幂等作用域';
COMMENT ON COLUMN api_idempotency.request_digest IS '请求体指纹（规范化哈希），用于检测同键不同请求体冲突';
COMMENT ON COLUMN api_idempotency.status IS '记录状态：COMPLETED/IN_PROGRESS/FAILED';
COMMENT ON COLUMN api_idempotency.response_payload IS '首次响应负载（JSONB：status + body，已脱敏）';
COMMENT ON COLUMN api_idempotency.expires_at IS '幂等记录过期时间，过期后可清理';
COMMENT ON COLUMN api_idempotency.created_at IS '记录创建时间（UTC）';
COMMENT ON COLUMN api_idempotency.completed_at IS '首次执行完成时间（UTC）';

-- ----------------------------------------------------------------------------
-- 可靠任务表：持久化后台任务状态机。job_id 业务键唯一。
-- 状态：PENDING(待领取)/RUNNING(执行中)/SUCCEEDED(成功)/RETRY_WAIT(退避等待)/DEAD(放弃)/CANCELLED(取消)。
-- 领取用 FOR UPDATE SKIP LOCKED；租约 leased_until 到期可重新领取。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS job_task (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id        VARCHAR(40)  NOT NULL UNIQUE,
    type          VARCHAR(64)  NOT NULL,
    payload       JSONB,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    max_attempts  INTEGER      NOT NULL DEFAULT 5,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    next_run_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    leased_until  TIMESTAMPTZ,
    leased_by     VARCHAR(64),
    trace_id      VARCHAR(64),
    principal_id  VARCHAR(40),
    asset_id      VARCHAR(40),
    error_code    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version   INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_job_task_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'RETRY_WAIT', 'DEAD', 'CANCELLED')),
    CONSTRAINT ck_job_task_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_job_task_max_attempts CHECK (max_attempts >= 1)
);

COMMENT ON TABLE job_task IS '可靠任务表，持久化后台任务状态机，由 Worker 以 FOR UPDATE SKIP LOCKED 领取';
COMMENT ON COLUMN job_task.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN job_task.job_id IS '任务业务 ID（job_+ULID），全局唯一';
COMMENT ON COLUMN job_task.type IS '任务类型，路由到对应幂等 JobHandler';
COMMENT ON COLUMN job_task.payload IS '任务负载（JSONB，已脱敏）';
COMMENT ON COLUMN job_task.status IS '任务状态：PENDING/RUNNING/SUCCEEDED/RETRY_WAIT/DEAD/CANCELLED';
COMMENT ON COLUMN job_task.max_attempts IS '最大尝试次数，超阈值进入 DEAD';
COMMENT ON COLUMN job_task.attempts IS '已尝试次数';
COMMENT ON COLUMN job_task.next_run_at IS '下次可领取时间（退避后），<=now 可领取';
COMMENT ON COLUMN job_task.leased_until IS '租约到期时间，到期且仍 RUNNING 可被重新领取（租约恢复）';
COMMENT ON COLUMN job_task.leased_by IS '领取者标识（Worker 实例）';
COMMENT ON COLUMN job_task.trace_id IS '分布式追踪 ID，全链路透传';
COMMENT ON COLUMN job_task.principal_id IS '发起主体 ID，全链路透传';
COMMENT ON COLUMN job_task.asset_id IS '关联资产 ID（可空），全链路透传';
COMMENT ON COLUMN job_task.error_code IS '最近一次失败错误码（进入 DEAD 时记录）';
COMMENT ON COLUMN job_task.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN job_task.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN job_task.row_version IS '乐观锁版本号';

-- 领取索引：状态 + 下次运行时间，支撑 FOR UPDATE SKIP LOCKED 高效领取。
CREATE INDEX IF NOT EXISTS idx_job_task_claim ON job_task (status, next_run_at);
-- 游标分页索引：按创建时间 + 主键键集分页。
CREATE INDEX IF NOT EXISTS idx_job_task_cursor ON job_task (created_at, id);
CREATE INDEX IF NOT EXISTS idx_job_task_principal ON job_task (principal_id);

-- ----------------------------------------------------------------------------
-- 任务尝试表：每次执行记录一行，用于排障与重试观测。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS job_attempt (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id        VARCHAR(40)  NOT NULL,
    attempt_no    INTEGER      NOT NULL,
    started_at    TIMESTAMPTZ  NOT NULL,
    ended_at      TIMESTAMPTZ,
    status        VARCHAR(16)  NOT NULL,
    error_message VARCHAR(1024),
    error_code    VARCHAR(64),
    duration_ms   BIGINT,
    CONSTRAINT ck_job_attempt_status CHECK (status IN ('SUCCESS', 'FAILED', 'DEAD')),
    CONSTRAINT ck_job_attempt_no CHECK (attempt_no >= 1)
);

COMMENT ON TABLE job_attempt IS '任务尝试表，每次执行记录一行，用于排障与重试观测';
COMMENT ON COLUMN job_attempt.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN job_attempt.job_id IS '关联任务业务 ID';
COMMENT ON COLUMN job_attempt.attempt_no IS '尝试序号，从 1 递增';
COMMENT ON COLUMN job_attempt.started_at IS '本次尝试开始时间（UTC）';
COMMENT ON COLUMN job_attempt.ended_at IS '本次尝试结束时间（UTC）';
COMMENT ON COLUMN job_attempt.status IS '尝试结果：SUCCESS/FAILED/DEAD';
COMMENT ON COLUMN job_attempt.error_message IS '失败错误信息（已脱敏）';
COMMENT ON COLUMN job_attempt.error_code IS '失败错误码';
COMMENT ON COLUMN job_attempt.duration_ms IS '本次尝试耗时（毫秒）';

CREATE INDEX IF NOT EXISTS idx_job_attempt_job ON job_attempt (job_id, attempt_no);
