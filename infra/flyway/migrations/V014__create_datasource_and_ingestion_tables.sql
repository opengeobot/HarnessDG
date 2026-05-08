-- V014__create_datasource_and_ingestion_tables.sql
-- 功能：创建数据源配置和数据接入任务表
-- 时间：2026-05-08
-- 作者：AxeXie

-- 数据源配置表
CREATE TABLE dss_data_source (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,          -- 数据源名称
    code                VARCHAR(100) NOT NULL,          -- 数据源编码
    source_type         VARCHAR(50) NOT NULL,           -- mysql/postgresql/hive/kafka/api/file
    connection_config   JSONB NOT NULL,                 -- 连接配置（密码加密存储）
    description         JSONB,                          -- 描述（多语言）
    status              VARCHAR(20) NOT NULL DEFAULT 'active',  -- active/inactive/error
    owner               VARCHAR(100),                   -- 负责人
    tags                JSONB,                          -- 标签
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE dss_data_source IS '数据源配置表';
COMMENT ON COLUMN dss_data_source.code IS '数据源编码，全局唯一';
COMMENT ON COLUMN dss_data_source.source_type IS '数据源类型：mysql/postgresql/hive/kafka/api/file';
COMMENT ON COLUMN dss_data_source.connection_config IS '连接配置 JSONB（密码需加密存储）';

CREATE UNIQUE INDEX uk_datasource_code ON dss_data_source(code) WHERE is_deleted = FALSE;
CREATE INDEX idx_datasource_type ON dss_data_source(source_type) WHERE is_deleted = FALSE;
CREATE INDEX idx_datasource_status ON dss_data_source(status) WHERE is_deleted = FALSE;

-- 数据接入任务表
CREATE TABLE dss_ingestion_task (
    id                  BIGSERIAL PRIMARY KEY,
    source_id           BIGINT NOT NULL REFERENCES dss_data_source(id),
    target_entity_id    BIGINT REFERENCES ont_entity(id),       -- 目标实体
    task_name           VARCHAR(200) NOT NULL,          -- 任务名称
    task_code           VARCHAR(100) NOT NULL,          -- 任务编码
    sync_mode           VARCHAR(20) NOT NULL,           -- full/incremental/event_driven
    source_table        VARCHAR(200),                   -- 源表名
    field_mapping       JSONB,                          -- 字段映射 JSON
    schedule_cron       VARCHAR(50),                    -- 调度频率
    seatunnel_job_id    VARCHAR(255),                   -- SeaTunnel 任务 ID
    dagster_run_id      VARCHAR(255),                   -- Dagster 运行 ID
    status              VARCHAR(30) NOT NULL DEFAULT 'draft',  -- draft/pending_approval/running/success/failed/paused
    last_sync_at        TIMESTAMPTZ,                    -- 最后同步时间
    last_sync_rows      BIGINT,                         -- 最后同步行数
    error_message       TEXT,                           -- 错误信息
    approval_instance_id BIGINT REFERENCES gov_approval_instance(id),  -- 关联审批
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE dss_ingestion_task IS '数据接入任务表';
COMMENT ON COLUMN dss_ingestion_task.sync_mode IS '同步模式：full/incremental/event_driven';
COMMENT ON COLUMN dss_ingestion_task.field_mapping IS '字段映射 JSON（源字段 -> 目标字段）';
COMMENT ON COLUMN dss_ingestion_task.status IS '任务状态：draft/pending_approval/running/success/failed/paused';

CREATE UNIQUE INDEX uk_ingestion_task_code ON dss_ingestion_task(task_code) WHERE is_deleted = FALSE;
CREATE INDEX idx_ingestion_task_source ON dss_ingestion_task(source_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_ingestion_task_entity ON dss_ingestion_task(target_entity_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_ingestion_task_status ON dss_ingestion_task(status) WHERE is_deleted = FALSE;
CREATE INDEX idx_ingestion_task_sync_at ON dss_ingestion_task(last_sync_at DESC) WHERE is_deleted = FALSE;
