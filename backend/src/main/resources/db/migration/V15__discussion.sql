-- ============================================================================
-- 功能: P1 Discussion/Comment 子系统 Schema。
--       资产讨论线程、评论、修订历史、订阅通知。
-- 时间: 2026-07-05
-- 作者: AxeXie
-- ============================================================================

-- ---- 讨论线程 ----

CREATE TABLE IF NOT EXISTS asset_discussion (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    thread_id       VARCHAR(40)  NOT NULL,
    asset_id        VARCHAR(40)  NOT NULL,
    title           VARCHAR(256) NOT NULL,
    created_by      VARCHAR(40)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    comment_count   INT          NOT NULL DEFAULT 0,
    last_comment_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_discussion_status CHECK (status IN ('OPEN', 'LOCKED', 'CLOSED')),
    CONSTRAINT ux_discussion_thread UNIQUE (thread_id)
);

CREATE INDEX IF NOT EXISTS ix_discussion_asset ON asset_discussion (asset_id, created_at DESC);

COMMENT ON TABLE asset_discussion IS '资产讨论线程表';
COMMENT ON COLUMN asset_discussion.thread_id IS '业务线程 ID（前缀 thr_）';
COMMENT ON COLUMN asset_discussion.asset_id IS '所属资产 ID';
COMMENT ON COLUMN asset_discussion.title IS '线程标题';
COMMENT ON COLUMN asset_discussion.status IS '线程状态：OPEN 开放 / LOCKED 锁定（Moderator）/ CLOSED 关闭';
COMMENT ON COLUMN asset_discussion.comment_count IS '评论计数（冗余优化）';

-- ---- 评论 ----

CREATE TABLE IF NOT EXISTS asset_comment (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comment_id      VARCHAR(40)  NOT NULL,
    thread_id       VARCHAR(40)  NOT NULL,
    parent_id       VARCHAR(40),
    asset_id        VARCHAR(40)  NOT NULL,
    body            TEXT         NOT NULL,
    created_by      VARCHAR(40)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'VISIBLE',
    revision_count  INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_comment_status CHECK (status IN ('VISIBLE', 'HIDDEN', 'RETRACTED')),
    CONSTRAINT ux_comment_comment UNIQUE (comment_id)
);

CREATE INDEX IF NOT EXISTS ix_comment_thread ON asset_comment (thread_id, created_at ASC);
CREATE INDEX IF NOT EXISTS ix_comment_asset ON asset_comment (asset_id);

COMMENT ON TABLE asset_comment IS '资产评论表';
COMMENT ON COLUMN asset_comment.comment_id IS '业务评论 ID（前缀 cmt_）';
COMMENT ON COLUMN asset_comment.thread_id IS '所属线程 ID';
COMMENT ON COLUMN asset_comment.parent_id IS '父评论 ID（可空，空表示顶层回复）';
COMMENT ON COLUMN asset_comment.body IS '评论正文（Markdown，最大 10000 字符）';
COMMENT ON COLUMN asset_comment.status IS '评论状态：VISIBLE / HIDDEN（Moderator）/ RETRACTED（作者撤回）';

-- ---- 评论修订历史 ----

CREATE TABLE IF NOT EXISTS asset_comment_revision (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    revision_id     VARCHAR(40)  NOT NULL,
    comment_id      VARCHAR(40)  NOT NULL,
    body            TEXT         NOT NULL,
    created_by      VARCHAR(40)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_comment_revision UNIQUE (revision_id)
);

CREATE INDEX IF NOT EXISTS ix_comment_revision_comment ON asset_comment_revision (comment_id, created_at DESC);

COMMENT ON TABLE asset_comment_revision IS '评论修订历史表（只追加，不可改/删）';

-- ---- 资产订阅 ----

CREATE TABLE IF NOT EXISTS asset_subscription (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id        VARCHAR(40)  NOT NULL,
    principal_id    VARCHAR(40)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_subscription UNIQUE (asset_id, principal_id)
);

COMMENT ON TABLE asset_subscription IS '资产订阅表（讨论/版本更新通知）';
