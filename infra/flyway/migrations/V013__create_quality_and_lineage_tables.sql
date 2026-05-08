-- V013__create_quality_and_lineage_tables.sql
-- 功能：创建质量规则/检查表和血缘节点/边表
-- 时间：2026-05-08
-- 作者：AxeXie

-- 质量规则表
CREATE TABLE gov_quality_rule (
    id              BIGSERIAL PRIMARY KEY,
    entity_id       BIGINT REFERENCES ont_entity(id),       -- 关联实体（可空）
    metric_id       BIGINT REFERENCES ont_metric(id),       -- 关联指标（可空）
    rule_type       VARCHAR(50) NOT NULL,                   -- not_null/unique/range/enum/regex/fluctuation/custom_sql
    rule_name       VARCHAR(200) NOT NULL,                  -- 规则名称
    rule_expression TEXT NOT NULL,                          -- JSON 格式规则定义
    threshold       JSONB,                                  -- 阈值配置
    severity        VARCHAR(20) NOT NULL DEFAULT 'warning', -- critical/warning/info
    status          VARCHAR(20) NOT NULL DEFAULT 'active',  -- active/inactive
    auto_generated  BOOLEAN NOT NULL DEFAULT FALSE,         -- 是否 AI 自动生成
    schedule_cron   VARCHAR(50),                            -- 检查频率
    description     TEXT,                                   -- 规则说明
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE gov_quality_rule IS '质量规则表';
COMMENT ON COLUMN gov_quality_rule.rule_type IS '规则类型：not_null/unique/range/enum/regex/fluctuation/custom_sql';
COMMENT ON COLUMN gov_quality_rule.rule_expression IS '规则表达式（JSON 格式）';
COMMENT ON COLUMN gov_quality_rule.threshold IS '阈值配置（JSONB）';
COMMENT ON COLUMN gov_quality_rule.severity IS '严重程度：critical/warning/info';
COMMENT ON COLUMN gov_quality_rule.auto_generated IS '是否 AI 自动生成';

CREATE INDEX idx_quality_rule_entity ON gov_quality_rule(entity_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_quality_rule_metric ON gov_quality_rule(metric_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_quality_rule_type ON gov_quality_rule(rule_type) WHERE is_deleted = FALSE;
CREATE INDEX idx_quality_rule_status ON gov_quality_rule(status) WHERE is_deleted = FALSE;

-- 质量检查记录表
CREATE TABLE gov_quality_check (
    id              BIGSERIAL PRIMARY KEY,
    rule_id         BIGINT NOT NULL REFERENCES gov_quality_rule(id),
    task_id         BIGINT,                                 -- 关联任务 ID（可空）
    check_result    VARCHAR(20) NOT NULL,                   -- pass/fail/error
    actual_value    JSONB,                                  -- 实际检测值
    expected_value  JSONB,                                  -- 期望值
    violated        BOOLEAN NOT NULL DEFAULT FALSE,         -- 是否违规
    error_message   TEXT,                                   -- 错误信息
    checked_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE gov_quality_check IS '质量检查记录表';
COMMENT ON COLUMN gov_quality_check.check_result IS '检查结果：pass/fail/error';
COMMENT ON COLUMN gov_quality_check.actual_value IS '实际检测值（JSONB）';
COMMENT ON COLUMN gov_quality_check.violated IS '是否违反规则';

CREATE INDEX idx_quality_check_rule ON gov_quality_check(rule_id);
CREATE INDEX idx_quality_check_result ON gov_quality_check(check_result);
CREATE INDEX idx_quality_check_checked_at ON gov_quality_check(checked_at DESC);

-- 血缘节点表
CREATE TABLE gov_lineage_node (
    id              BIGSERIAL PRIMARY KEY,
    node_type       VARCHAR(50) NOT NULL,           -- data_source/table/column/metric/entity/report
    node_key        VARCHAR(255) NOT NULL,          -- 唯一标识（如 schema.table.column）
    name            JSONB,                          -- 节点名称（多语言）
    entity_id       BIGINT REFERENCES ont_entity(id),       -- 关联实体（可空）
    metric_id       BIGINT REFERENCES ont_metric(id),       -- 关联指标（可空）
    data_source     VARCHAR(100),                   -- 数据源（可空）
    extra           JSONB,                          -- 扩展属性
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE gov_lineage_node IS '血缘节点表';
COMMENT ON COLUMN gov_lineage_node.node_type IS '节点类型：data_source/table/column/metric/entity/report';
COMMENT ON COLUMN gov_lineage_node.node_key IS '唯一标识符（如 schema.table.column）';
COMMENT ON COLUMN gov_lineage_node.name IS '节点名称（多语言 JSONB）';

CREATE UNIQUE INDEX uk_lineage_node_key ON gov_lineage_node(node_type, node_key) WHERE is_deleted = FALSE;
CREATE INDEX idx_lineage_node_entity ON gov_lineage_node(entity_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_lineage_node_metric ON gov_lineage_node(metric_id) WHERE is_deleted = FALSE;

-- 血缘边表
CREATE TABLE gov_lineage_edge (
    id              BIGSERIAL PRIMARY KEY,
    source_node_id  BIGINT NOT NULL REFERENCES gov_lineage_node(id),
    target_node_id  BIGINT NOT NULL REFERENCES gov_lineage_node(id),
    edge_type       VARCHAR(50) NOT NULL,           -- transforms/depends_on/derives_from/feeds_into
    transform_logic TEXT,                           -- 转换逻辑描述
    extra           JSONB,                          -- 扩展属性
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE gov_lineage_edge IS '血缘边表';
COMMENT ON COLUMN gov_lineage_edge.edge_type IS '边类型：transforms/depends_on/derives_from/feeds_into';
COMMENT ON COLUMN gov_lineage_edge.transform_logic IS '转换逻辑描述或 SQL 片段';

CREATE INDEX idx_lineage_edge_source ON gov_lineage_edge(source_node_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_lineage_edge_target ON gov_lineage_edge(target_node_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_lineage_edge_type ON gov_lineage_edge(edge_type) WHERE is_deleted = FALSE;
