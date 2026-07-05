-- ============================================================================
-- 功能: P3 发布治理 Schema。
--       创建 validation_report / publish_request / review_decision 表。
--       添加已发布版本不可变触发器。
-- 时间: 2026-07-05
-- 作者: AxeXie
-- ============================================================================

-- ===========================================================================
-- validation_report: 校验报告
-- ===========================================================================

CREATE TABLE IF NOT EXISTS validation_report (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id       VARCHAR(40)  NOT NULL,
    version_id      VARCHAR(40)  NOT NULL,
    policy_version  VARCHAR(32)  NOT NULL DEFAULT 'v1',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    findings        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_validation_report_report_id CHECK (report_id LIKE 'vrp_%'),
    CONSTRAINT ck_validation_report_version_id CHECK (version_id LIKE 'ver_%'),
    CONSTRAINT ck_validation_report_status CHECK (status IN ('PENDING', 'PASSED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_validation_report_version ON validation_report (version_id);

COMMENT ON TABLE validation_report IS '版本校验报告，记录 Card/Manifest/Artifact/DVC/License/Sensitivity 校验结果';
COMMENT ON COLUMN validation_report.report_id IS '报告业务 ID（vrp_ 前缀）';
COMMENT ON COLUMN validation_report.status IS '校验状态：PENDING/PASSED/FAILED';
COMMENT ON COLUMN validation_report.findings IS '结构化校验发现（JSONB 数组）';

-- ===========================================================================
-- publish_request: 发布请求
-- ===========================================================================

CREATE TABLE IF NOT EXISTS publish_request (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id      VARCHAR(40)  NOT NULL,
    version_id      VARCHAR(40)  NOT NULL,
    frozen_digest   VARCHAR(128) NOT NULL,
    policy_version  VARCHAR(32)  NOT NULL DEFAULT 'v1',
    status          VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    submitted_by    VARCHAR(40)  NOT NULL,
    submitted_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ,

    CONSTRAINT ck_publish_request_request_id CHECK (request_id LIKE 'pub_%'),
    CONSTRAINT ck_publish_request_version_id CHECK (version_id LIKE 'ver_%'),
    CONSTRAINT ck_publish_request_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'PUBLISHING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ux_publish_request_version UNIQUE (version_id)
);

CREATE INDEX IF NOT EXISTS ix_publish_request_status ON publish_request (status) WHERE status IN ('SUBMITTED', 'APPROVED');

COMMENT ON TABLE publish_request IS '发布请求，每个版本仅一个请求（唯一约束）';
COMMENT ON COLUMN publish_request.request_id IS '请求业务 ID（pub_ 前缀）';
COMMENT ON COLUMN publish_request.frozen_digest IS '冻结时的 Manifest 摘要';
COMMENT ON COLUMN publish_request.status IS '状态：SUBMITTED/APPROVED/REJECTED/PUBLISHING/PUBLISHED/FAILED';

-- ===========================================================================
-- review_decision: 审批决策
-- ===========================================================================

CREATE TABLE IF NOT EXISTS review_decision (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id       VARCHAR(40)  NOT NULL,
    request_id      VARCHAR(40)  NOT NULL,
    reviewer_id     VARCHAR(40)  NOT NULL,
    decision        VARCHAR(16)  NOT NULL,
    comments        TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_review_decision_review_id CHECK (review_id LIKE 'rvw_%'),
    CONSTRAINT ck_review_decision_request_id CHECK (request_id LIKE 'pub_%'),
    CONSTRAINT ck_review_decision_decision CHECK (decision IN ('APPROVE', 'REJECT', 'REQUEST_CHANGES')),
    CONSTRAINT ux_review_decision_request_reviewer UNIQUE (request_id, reviewer_id)
);

CREATE INDEX IF NOT EXISTS ix_review_decision_request ON review_decision (request_id);

COMMENT ON TABLE review_decision IS '审批决策记录，每个审批人对每个请求仅一次决策';
COMMENT ON COLUMN review_decision.review_id IS '审批业务 ID（rev_ 前缀）';
COMMENT ON COLUMN review_decision.decision IS '决策：APPROVE/REJECT/REQUEST_CHANGES';

-- ===========================================================================
-- 已发布版本不可变触发器
-- ===========================================================================

CREATE OR REPLACE FUNCTION prevent_published_version_update()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'PUBLISHED' AND NEW.status = 'PUBLISHED' THEN
        -- 允许同状态内字段更新（如弃用/归档），但禁止修改关键字段
        IF OLD.manifest_digest IS DISTINCT FROM NEW.manifest_digest
           OR OLD.git_tag IS DISTINCT FROM NEW.git_tag
           OR OLD.source_commit IS DISTINCT FROM NEW.source_commit
           OR OLD.published_at IS DISTINCT FROM NEW.published_at
           OR OLD.published_by IS DISTINCT FROM NEW.published_by THEN
            RAISE EXCEPTION 'published version immutable: cannot modify digest/tag/commit/publish fields';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_published_version_update
    BEFORE UPDATE ON asset_version
    FOR EACH ROW
    WHEN (OLD.status = 'PUBLISHED')
    EXECUTE FUNCTION prevent_published_version_update();

COMMENT ON FUNCTION prevent_published_version_update() IS '阻止修改已发布版本的关键字段（digest/tag/commit/publish 信息）';
