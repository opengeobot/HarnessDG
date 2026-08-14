-- V8__interaction.sql
-- 互动强事实表（03 §6.1）：点赞/收藏 UNIQUE 约束兜底并发（Toggle 不得切换两次）；
-- feedbacks 带审核状态与快照；visit_events 按 repository + visitor_hash + 30 分钟窗口去重
-- （03 §6.2 / 06 §7.2）。统计投影 repository_stats 由这些强事实表幂等重建。

-- ---------- 点赞 ----------
CREATE TABLE repository_likes (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    repository_id BIGINT NOT NULL REFERENCES repositories(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, repository_id)
);
-- UNIQUE(user_id, repository_id) 已覆盖 user 方向查询，此处仅补 repository 方向（03 §9）
CREATE INDEX ix_likes_repo ON repository_likes(repository_id);

-- ---------- 收藏 ----------
CREATE TABLE repository_favorites (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    repository_id BIGINT NOT NULL REFERENCES repositories(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, repository_id)
);
CREATE INDEX ix_favorites_repo ON repository_favorites(repository_id);

-- ---------- 反馈 ----------
CREATE TABLE feedbacks (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID UNIQUE NOT NULL,
    repository_id     BIGINT NOT NULL REFERENCES repositories(id),
    user_id           BIGINT NOT NULL REFERENCES users(id),
    content           TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 10000),
    author_snapshot   JSONB,
    -- v1 无审核端点（04 无契约），默认 approved；审核管理面留待后续契约版本
    moderation_status VARCHAR(16) NOT NULL DEFAULT 'approved'
                      CHECK (moderation_status IN ('pending', 'approved', 'rejected', 'removed')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX ix_feedbacks_repo ON feedbacks(repository_id, created_at DESC, id DESC);

-- ---------- 访问事件（30 分钟窗口去重） ----------
CREATE TABLE visit_events (
    id            BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES repositories(id),
    visitor_hash  VARCHAR(64) NOT NULL,
    window_start  TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repository_id, visitor_hash, window_start)
);
CREATE INDEX ix_visit_repo ON visit_events(repository_id, window_start);
