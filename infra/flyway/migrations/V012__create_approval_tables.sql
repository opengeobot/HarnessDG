-- V012__create_approval_tables.sql
-- 功能：创建审批流引擎相关表（模板、实例、步骤、发布记录）
-- 时间：2026-05-08
-- 作者：AxeXie

-- 审批模板表
CREATE TABLE gov_approval_template (
    id              BIGSERIAL PRIMARY KEY,
    business_type   VARCHAR(50) NOT NULL,       -- metric_publish / data_ingestion / permission_request
    code            VARCHAR(100) NOT NULL,      -- 模板编码
    name            JSONB NOT NULL,             -- 模板名称（多语言）
    description     JSONB,                      -- 模板描述
    steps_json      JSONB NOT NULL,             -- 步骤定义 [{order, role, timeout}]
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE gov_approval_template IS '审批模板表';
COMMENT ON COLUMN gov_approval_template.business_type IS '业务类型：metric_publish/data_ingestion/permission_request';
COMMENT ON COLUMN gov_approval_template.code IS '模板编码，全局唯一';
COMMENT ON COLUMN gov_approval_template.name IS '模板名称（多语言 JSONB）';
COMMENT ON COLUMN gov_approval_template.steps_json IS '步骤定义 JSON 数组';

CREATE UNIQUE INDEX uk_approval_template_code ON gov_approval_template(code) WHERE is_deleted = FALSE;
CREATE INDEX idx_approval_template_business ON gov_approval_template(business_type) WHERE is_deleted = FALSE;

-- 审批实例表
CREATE TABLE gov_approval_instance (
    id                  BIGSERIAL PRIMARY KEY,
    template_id         BIGINT NOT NULL REFERENCES gov_approval_template(id),
    business_type       VARCHAR(50) NOT NULL,       -- 业务类型
    business_id         VARCHAR(100) NOT NULL,      -- 关联业务对象 ID
    title               VARCHAR(255) NOT NULL,      -- 审批标题
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',  -- draft/pending/approved/rejected/cancelled
    initiator           VARCHAR(100) NOT NULL,      -- 发起人
    current_step        INT NOT NULL DEFAULT 1,     -- 当前步骤
    result_note         TEXT,                       -- 结果备注
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE gov_approval_instance IS '审批实例表';
COMMENT ON COLUMN gov_approval_instance.business_id IS '关联业务对象 ID（如指标ID、接入任务ID）';
COMMENT ON COLUMN gov_approval_instance.status IS '审批状态：draft/pending/approved/rejected/cancelled';

CREATE INDEX idx_approval_instance_business ON gov_approval_instance(business_type, business_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_approval_instance_initiator ON gov_approval_instance(initiator) WHERE is_deleted = FALSE;
CREATE INDEX idx_approval_instance_status ON gov_approval_instance(status) WHERE is_deleted = FALSE;

-- 审批步骤表
CREATE TABLE gov_approval_step (
    id              BIGSERIAL PRIMARY KEY,
    instance_id     BIGINT NOT NULL REFERENCES gov_approval_instance(id),
    step_order      INT NOT NULL,                 -- 步骤顺序
    approver_role   VARCHAR(50) NOT NULL,         -- 审批角色
    approver_user   VARCHAR(100),                 -- 审批人
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending/approved/rejected/skipped
    action          VARCHAR(20),                  -- approve/reject
    comment         TEXT,                         -- 审批意见
    decided_at      TIMESTAMPTZ,                  -- 决定时间
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE gov_approval_step IS '审批步骤表';
COMMENT ON COLUMN gov_approval_step.approver_role IS '审批角色（如 data_owner, governance_lead）';
COMMENT ON COLUMN gov_approval_step.status IS '步骤状态：pending/approved/rejected/skipped';

CREATE INDEX idx_approval_step_instance ON gov_approval_step(instance_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_approval_step_order ON gov_approval_step(instance_id, step_order) WHERE is_deleted = FALSE;
CREATE INDEX idx_approval_step_approver ON gov_approval_step(approver_user) WHERE is_deleted = FALSE;

-- 发布记录表
CREATE TABLE gov_publish_record (
    id                      BIGSERIAL PRIMARY KEY,
    business_type           VARCHAR(50) NOT NULL,       -- 业务类型
    business_id             VARCHAR(100) NOT NULL,      -- 业务对象 ID
    version                 INT NOT NULL DEFAULT 1,     -- 版本号
    published_by            VARCHAR(100) NOT NULL,      -- 发布人
    published_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approval_instance_id    BIGINT REFERENCES gov_approval_instance(id),
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    created_by              VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by              VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE gov_publish_record IS '发布记录表';
COMMENT ON COLUMN gov_publish_record.business_type IS '业务类型：metric/ingestion_task 等';
COMMENT ON COLUMN gov_publish_record.approval_instance_id IS '关联的审批实例 ID';

CREATE INDEX idx_publish_record_business ON gov_publish_record(business_type, business_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_publish_record_published_at ON gov_publish_record(published_at DESC) WHERE is_deleted = FALSE;
