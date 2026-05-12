-- V004__create_task_tables.sql
-- 功能：创建业务任务实例表
-- 时间：2026-05-07
-- 作者：AxeXie

CREATE TABLE biz_task (
    id              BIGSERIAL PRIMARY KEY,
    task_code       VARCHAR(100) NOT NULL,
    task_type       VARCHAR(50) NOT NULL,
    title           JSONB NOT NULL,
    description     JSONB,
    status          VARCHAR(30) NOT NULL DEFAULT 'draft',
    priority        VARCHAR(20) NOT NULL DEFAULT 'medium',
    input_params    JSONB,
    execution_plan  JSONB,
    result          JSONB,
    initiator       VARCHAR(100) NOT NULL,
    assignee        VARCHAR(100),
    data_domain     VARCHAR(100),
    entity_id       BIGINT,
    metric_id       BIGINT,
    dagster_run_id  VARCHAR(255),
    agent_session_id VARCHAR(255),
    trace_id        VARCHAR(100),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(100) NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE biz_task IS '业务任务实例表';
COMMENT ON COLUMN biz_task.id IS '主键ID';
COMMENT ON COLUMN biz_task.task_code IS '任务编码（唯一）';
COMMENT ON COLUMN biz_task.task_type IS '任务类型';
COMMENT ON COLUMN biz_task.title IS '任务标题（多语言JSONB）';
COMMENT ON COLUMN biz_task.description IS '任务描述（多语言JSONB）';
COMMENT ON COLUMN biz_task.status IS '任务状态';
COMMENT ON COLUMN biz_task.priority IS '优先级（low/medium/high/urgent）';
COMMENT ON COLUMN biz_task.input_params IS '输入参数（JSONB）';
COMMENT ON COLUMN biz_task.execution_plan IS '执行计划（JSONB）';
COMMENT ON COLUMN biz_task.result IS '执行结果（JSONB）';
COMMENT ON COLUMN biz_task.initiator IS '发起人';
COMMENT ON COLUMN biz_task.assignee IS '指派人';
COMMENT ON COLUMN biz_task.data_domain IS '所属数据域';
COMMENT ON COLUMN biz_task.entity_id IS '关联实体ID';
COMMENT ON COLUMN biz_task.metric_id IS '关联指标ID';
COMMENT ON COLUMN biz_task.dagster_run_id IS 'Dagster运行ID';
COMMENT ON COLUMN biz_task.agent_session_id IS 'Agent会话ID';
COMMENT ON COLUMN biz_task.trace_id IS '追踪ID（全链路关联）';
COMMENT ON COLUMN biz_task.started_at IS '开始时间';
COMMENT ON COLUMN biz_task.completed_at IS '完成时间';
COMMENT ON COLUMN biz_task.is_deleted IS '逻辑删除标识';
COMMENT ON COLUMN biz_task.created_by IS '创建人';
COMMENT ON COLUMN biz_task.updated_by IS '最后更新人';
COMMENT ON COLUMN biz_task.created_at IS '创建时间';
COMMENT ON COLUMN biz_task.updated_at IS '最后更新时间';

CREATE UNIQUE INDEX uk_task_code ON biz_task(task_code) WHERE is_deleted = FALSE;
CREATE INDEX idx_task_type_status ON biz_task(task_type, status) WHERE is_deleted = FALSE;
CREATE INDEX idx_task_initiator ON biz_task(initiator, created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_task_trace ON biz_task(trace_id) WHERE trace_id IS NOT NULL;
