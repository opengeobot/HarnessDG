-- ============================================================================
-- 功能: P0-B notification 迁移——站内通知、Webhook 投递记录与 Outbox 事件。
--       站内通知写 notification 表；事件写 outbox_event（与核心事务原子提交，保证不丢）；
--       Outbox 投递器定时扫描未处理事件，对 Webhook 目标签名投递（HMAC-SHA256 + 时间戳 + deliveryId），
--       失败指数退避重试，超阈值 DEAD。出站做 SSRF 防护（禁止内网/保留地址）。
-- 时间: 2026-06-30
-- 作者: AxeXie
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 站内通知表：目标 principal_id 的通知记录。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notification_id  VARCHAR(40)  NOT NULL UNIQUE,
    principal_id     VARCHAR(40)  NOT NULL,
    event_type       VARCHAR(64)  NOT NULL,
    i18n_key         VARCHAR(128) NOT NULL,
    parameters       JSONB,
    severity         VARCHAR(16)  NOT NULL DEFAULT 'INFO',
    read             SMALLINT     NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at          TIMESTAMPTZ,
    row_version      INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_notification_read CHECK (read IN (0, 1)),
    CONSTRAINT ck_notification_severity CHECK (severity IN ('INFO', 'WARN', 'ERROR', 'CRITICAL'))
);

COMMENT ON TABLE notification IS '站内通知表，承载目标主体的通知记录';
COMMENT ON COLUMN notification.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN notification.notification_id IS '通知业务 ID（ntf_+ULID），全局唯一';
COMMENT ON COLUMN notification.principal_id IS '目标主体 ID';
COMMENT ON COLUMN notification.event_type IS '事件类型（如 JOB_DEAD、AGENT_ACCESS_DENIED）';
COMMENT ON COLUMN notification.i18n_key IS '国际化文案键，模板与渠道解耦';
COMMENT ON COLUMN notification.parameters IS '文案参数（JSONB，已脱敏）';
COMMENT ON COLUMN notification.severity IS '严重等级：INFO/WARN/ERROR/CRITICAL';
COMMENT ON COLUMN notification.read IS '是否已读：0 未读 / 1 已读';
COMMENT ON COLUMN notification.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN notification.read_at IS '标记已读时间（UTC）';
COMMENT ON COLUMN notification.row_version IS '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_notification_principal ON notification (principal_id, created_at, id);

-- ----------------------------------------------------------------------------
-- Outbox 事件表：事件在产生事件的同一数据库事务内写入，再由后台可靠任务轮询发布。
-- processed_at IS NULL 表示待投递。消费方以 eventId 幂等去重。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_event (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        VARCHAR(40)  NOT NULL UNIQUE,
    aggregate_type  VARCHAR(32)  NOT NULL,
    aggregate_id    VARCHAR(40)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload         JSONB,
    headers         JSONB,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,
    trace_id        VARCHAR(64),
    principal_id    VARCHAR(40)
);

COMMENT ON TABLE outbox_event IS 'Outbox 事件表，与核心事务原子写入，保证事件不丢';
COMMENT ON COLUMN outbox_event.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN outbox_event.event_id IS '事件全局唯一 ID（evt_+ULID），消费方据此幂等去重';
COMMENT ON COLUMN outbox_event.aggregate_type IS '聚合根类型（如 JOB、AGENT、SYSTEM_DEPENDENCY）';
COMMENT ON COLUMN outbox_event.aggregate_id IS '聚合根稳定业务 ID';
COMMENT ON COLUMN outbox_event.event_type IS '事件类型（与事件契约 EventType 对齐）';
COMMENT ON COLUMN outbox_event.payload IS '事件负载（JSONB，已脱敏）';
COMMENT ON COLUMN outbox_event.headers IS '事件头（JSONB，如 webhook 目标等路由信息）';
COMMENT ON COLUMN outbox_event.occurred_at IS '事件业务发生时间（UTC）';
COMMENT ON COLUMN outbox_event.processed_at IS '事件处理完成时间，NULL 表示待投递';
COMMENT ON COLUMN outbox_event.trace_id IS '分布式追踪 ID';
COMMENT ON COLUMN outbox_event.principal_id IS '触发事件的主体 ID（可空）';

-- 待投递索引：仅扫描 processed_at IS NULL 的事件。
CREATE INDEX IF NOT EXISTS idx_outbox_event_pending ON outbox_event ((processed_at IS NULL), occurred_at)
    WHERE processed_at IS NULL;

-- ----------------------------------------------------------------------------
-- Webhook 投递记录表：每次事件对某 target_url 的投递状态机。
-- 签名头 X-AIHub-Signature: t=<timestamp>,v1=<hmac>；失败指数退避重试，超阈值 DEAD。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS webhook_delivery (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id         VARCHAR(40)  NOT NULL UNIQUE,
    event_id            VARCHAR(40)  NOT NULL,
    target_url          VARCHAR(1024) NOT NULL,
    payload             JSONB,
    signature_header    VARCHAR(512),
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts            INTEGER      NOT NULL DEFAULT 0,
    last_response_code  INTEGER,
    last_error          VARCHAR(1024),
    next_retry_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_webhook_delivery_status CHECK (
        status IN ('PENDING', 'DELIVERED', 'FAILED', 'DEAD'))
);

COMMENT ON TABLE webhook_delivery IS 'Webhook 投递记录表，承载出站投递状态机';
COMMENT ON COLUMN webhook_delivery.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN webhook_delivery.delivery_id IS '投递唯一 ID（whk_+ULID），接收方据此幂等';
COMMENT ON COLUMN webhook_delivery.event_id IS '关联事件 ID';
COMMENT ON COLUMN webhook_delivery.target_url IS '投递目标 URL（已校验非内网/保留地址）';
COMMENT ON COLUMN webhook_delivery.payload IS '投递负载（JSONB，已脱敏）';
COMMENT ON COLUMN webhook_delivery.signature_header IS '签名头（X-AIHub-Signature: t=,v1=）';
COMMENT ON COLUMN webhook_delivery.status IS '投递状态：PENDING/DELIVERED/FAILED/DEAD';
COMMENT ON COLUMN webhook_delivery.attempts IS '已尝试次数';
COMMENT ON COLUMN webhook_delivery.last_response_code IS '最近一次 HTTP 响应码';
COMMENT ON COLUMN webhook_delivery.last_error IS '最近一次失败错误信息（已脱敏）';
COMMENT ON COLUMN webhook_delivery.next_retry_at IS '下次重试时间（退避后）';
COMMENT ON COLUMN webhook_delivery.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN webhook_delivery.updated_at IS '更新时间（UTC）';

CREATE INDEX IF NOT EXISTS idx_webhook_delivery_event ON webhook_delivery (event_id);
CREATE INDEX IF NOT EXISTS idx_webhook_delivery_retry ON webhook_delivery (status, next_retry_at);
