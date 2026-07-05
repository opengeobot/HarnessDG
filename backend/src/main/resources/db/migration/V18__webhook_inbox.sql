-- V18: Gitea Webhook Inbox
-- 功能: 接收 Gitea Webhook 事件，幂等入库后异步处理。
-- 时间: 2026-07-04

-- Webhook 投递记录（幂等键 = delivery_id）
CREATE TABLE IF NOT EXISTS webhook_inbox (
    id              BIGSERIAL       PRIMARY KEY,
    delivery_id     VARCHAR(64)     NOT NULL,
    event_type      VARCHAR(64)     NOT NULL,
    source          VARCHAR(32)     NOT NULL DEFAULT 'gitea',
    signature_valid BOOLEAN         NOT NULL DEFAULT FALSE,
    payload         JSONB           NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'DISCARDED')),
    error_message   TEXT,
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,

    CONSTRAINT uq_webhook_delivery UNIQUE (delivery_id)
);

-- 索引：待处理队列
CREATE INDEX IF NOT EXISTS ix_webhook_inbox_pending
    ON webhook_inbox (received_at)
    WHERE status = 'PENDING';

-- 索引：按事件类型查询
CREATE INDEX IF NOT EXISTS ix_webhook_inbox_event_type
    ON webhook_inbox (event_type);
