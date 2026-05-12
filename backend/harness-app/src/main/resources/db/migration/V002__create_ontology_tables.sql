-- V002__create_ontology_tables.sql
-- 功能：创建本体域核心表（实体、指标、维度、关系）
-- 时间：2026-05-07
-- 作者：AxeXie

-- 业务实体表
CREATE TABLE ont_entity (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(100) NOT NULL,
    name          JSONB NOT NULL,
    description   JSONB,
    entity_type   VARCHAR(50) NOT NULL,
    data_domain   VARCHAR(100) NOT NULL,
    owner         VARCHAR(100),
    status        VARCHAR(20) NOT NULL DEFAULT 'draft',
    version       INT NOT NULL DEFAULT 1,
    tags          JSONB,
    extra         JSONB,
    embedding     vector(1536),
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    created_by    VARCHAR(100) NOT NULL,
    updated_by    VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ont_entity IS '本体业务实体表';
COMMENT ON COLUMN ont_entity.id IS '主键ID';
COMMENT ON COLUMN ont_entity.code IS '实体编码（唯一）';
COMMENT ON COLUMN ont_entity.name IS '实体名称（多语言JSONB）';
COMMENT ON COLUMN ont_entity.description IS '实体描述（多语言JSONB）';
COMMENT ON COLUMN ont_entity.entity_type IS '实体类型';
COMMENT ON COLUMN ont_entity.data_domain IS '所属数据域';
COMMENT ON COLUMN ont_entity.owner IS '负责人';
COMMENT ON COLUMN ont_entity.status IS '状态（draft/active/deprecated）';
COMMENT ON COLUMN ont_entity.version IS '版本号';
COMMENT ON COLUMN ont_entity.tags IS '标签（JSONB）';
COMMENT ON COLUMN ont_entity.extra IS '扩展属性（JSONB）';
COMMENT ON COLUMN ont_entity.embedding IS '向量嵌入（用于语义搜索）';
COMMENT ON COLUMN ont_entity.is_deleted IS '逻辑删除标识';
COMMENT ON COLUMN ont_entity.created_by IS '创建人';
COMMENT ON COLUMN ont_entity.updated_by IS '最后更新人';
COMMENT ON COLUMN ont_entity.created_at IS '创建时间';
COMMENT ON COLUMN ont_entity.updated_at IS '最后更新时间';

CREATE UNIQUE INDEX uk_entity_code ON ont_entity(code) WHERE is_deleted = FALSE;
CREATE INDEX idx_entity_domain ON ont_entity(data_domain, status) WHERE is_deleted = FALSE;

-- 业务指标表
CREATE TABLE ont_metric (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(100) NOT NULL,
    name            JSONB NOT NULL,
    description     JSONB,
    entity_id       BIGINT REFERENCES ont_entity(id),
    metric_type     VARCHAR(50) NOT NULL,
    agg_method      VARCHAR(50),
    expression      TEXT,
    grain           VARCHAR(50),
    unit            VARCHAR(50),
    data_domain     VARCHAR(100) NOT NULL,
    owner           VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'draft',
    version         INT NOT NULL DEFAULT 1,
    dagster_asset_key VARCHAR(255),
    quality_rules   JSONB,
    tags            JSONB,
    extra           JSONB,
    embedding       vector(1536),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(100) NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ont_metric IS '本体业务指标表';
COMMENT ON COLUMN ont_metric.id IS '主键ID';
COMMENT ON COLUMN ont_metric.code IS '指标编码（唯一）';
COMMENT ON COLUMN ont_metric.name IS '指标名称（多语言JSONB）';
COMMENT ON COLUMN ont_metric.description IS '指标描述（多语言JSONB）';
COMMENT ON COLUMN ont_metric.entity_id IS '所属实体ID';
COMMENT ON COLUMN ont_metric.metric_type IS '指标类型（atomic/derived/composite）';
COMMENT ON COLUMN ont_metric.agg_method IS '聚合方式（sum/avg/count等）';
COMMENT ON COLUMN ont_metric.expression IS '计算表达式';
COMMENT ON COLUMN ont_metric.grain IS '时间粒度';
COMMENT ON COLUMN ont_metric.unit IS '单位';
COMMENT ON COLUMN ont_metric.data_domain IS '所属数据域';
COMMENT ON COLUMN ont_metric.owner IS '负责人';
COMMENT ON COLUMN ont_metric.status IS '状态（draft/active/deprecated）';
COMMENT ON COLUMN ont_metric.version IS '版本号';
COMMENT ON COLUMN ont_metric.dagster_asset_key IS 'Dagster资产键';
COMMENT ON COLUMN ont_metric.quality_rules IS '关联质量规则（JSONB）';
COMMENT ON COLUMN ont_metric.tags IS '标签（JSONB）';
COMMENT ON COLUMN ont_metric.extra IS '扩展属性（JSONB）';
COMMENT ON COLUMN ont_metric.embedding IS '向量嵌入（用于语义搜索）';
COMMENT ON COLUMN ont_metric.is_deleted IS '逻辑删除标识';
COMMENT ON COLUMN ont_metric.created_by IS '创建人';
COMMENT ON COLUMN ont_metric.updated_by IS '最后更新人';
COMMENT ON COLUMN ont_metric.created_at IS '创建时间';
COMMENT ON COLUMN ont_metric.updated_at IS '最后更新时间';

CREATE UNIQUE INDEX uk_metric_code ON ont_metric(code) WHERE is_deleted = FALSE;
CREATE INDEX idx_metric_entity ON ont_metric(entity_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_metric_domain ON ont_metric(data_domain, status) WHERE is_deleted = FALSE;

-- 分析维度表
CREATE TABLE ont_dimension (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(100) NOT NULL,
    name            JSONB NOT NULL,
    description     JSONB,
    dimension_type  VARCHAR(50) NOT NULL,
    data_type       VARCHAR(50) NOT NULL DEFAULT 'string',
    hierarchy_levels JSONB,
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    embedding       vector(1536),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(100) NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ont_dimension IS '本体分析维度表';
COMMENT ON COLUMN ont_dimension.id IS '主键ID';
COMMENT ON COLUMN ont_dimension.code IS '维度编码（唯一）';
COMMENT ON COLUMN ont_dimension.name IS '维度名称（多语言JSONB）';
COMMENT ON COLUMN ont_dimension.description IS '维度描述（多语言JSONB）';
COMMENT ON COLUMN ont_dimension.dimension_type IS '维度类型';
COMMENT ON COLUMN ont_dimension.data_type IS '数据类型（string/int/date等）';
COMMENT ON COLUMN ont_dimension.hierarchy_levels IS '层级结构（JSONB）';
COMMENT ON COLUMN ont_dimension.status IS '状态（active/inactive）';
COMMENT ON COLUMN ont_dimension.embedding IS '向量嵌入（用于语义搜索）';
COMMENT ON COLUMN ont_dimension.is_deleted IS '逻辑删除标识';
COMMENT ON COLUMN ont_dimension.created_by IS '创建人';
COMMENT ON COLUMN ont_dimension.updated_by IS '最后更新人';
COMMENT ON COLUMN ont_dimension.created_at IS '创建时间';
COMMENT ON COLUMN ont_dimension.updated_at IS '最后更新时间';

CREATE UNIQUE INDEX uk_dimension_code ON ont_dimension(code) WHERE is_deleted = FALSE;

-- 实体关系表
CREATE TABLE ont_relation (
    id                BIGSERIAL PRIMARY KEY,
    source_entity_id  BIGINT NOT NULL REFERENCES ont_entity(id),
    target_entity_id  BIGINT NOT NULL REFERENCES ont_entity(id),
    relation_type     VARCHAR(50) NOT NULL,
    name              JSONB,
    description       JSONB,
    cardinality       VARCHAR(20),
    extra             JSONB,
    is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
    created_by        VARCHAR(100) NOT NULL,
    updated_by        VARCHAR(100) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ont_relation IS '本体实体关系表';
COMMENT ON COLUMN ont_relation.id IS '主键ID';
COMMENT ON COLUMN ont_relation.source_entity_id IS '源实体ID';
COMMENT ON COLUMN ont_relation.target_entity_id IS '目标实体ID';
COMMENT ON COLUMN ont_relation.relation_type IS '关系类型';
COMMENT ON COLUMN ont_relation.name IS '关系名称（多语言JSONB）';
COMMENT ON COLUMN ont_relation.description IS '关系描述（多语言JSONB）';
COMMENT ON COLUMN ont_relation.cardinality IS '基数（1:1/1:N/N:M）';
COMMENT ON COLUMN ont_relation.extra IS '扩展属性（JSONB）';
COMMENT ON COLUMN ont_relation.is_deleted IS '逻辑删除标识';
COMMENT ON COLUMN ont_relation.created_by IS '创建人';
COMMENT ON COLUMN ont_relation.updated_by IS '最后更新人';
COMMENT ON COLUMN ont_relation.created_at IS '创建时间';
COMMENT ON COLUMN ont_relation.updated_at IS '最后更新时间';

CREATE INDEX idx_relation_source ON ont_relation(source_entity_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_relation_target ON ont_relation(target_entity_id) WHERE is_deleted = FALSE;

-- 指标-维度关联表
CREATE TABLE ont_metric_dimension (
    id            BIGSERIAL PRIMARY KEY,
    metric_id     BIGINT NOT NULL REFERENCES ont_metric(id),
    dimension_id  BIGINT NOT NULL REFERENCES ont_dimension(id),
    is_required   BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order    INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE ont_metric_dimension IS '指标维度关联表';
COMMENT ON COLUMN ont_metric_dimension.id IS '主键ID';
COMMENT ON COLUMN ont_metric_dimension.metric_id IS '指标ID';
COMMENT ON COLUMN ont_metric_dimension.dimension_id IS '维度ID';
COMMENT ON COLUMN ont_metric_dimension.is_required IS '是否必需';
COMMENT ON COLUMN ont_metric_dimension.sort_order IS '排序序号';

CREATE UNIQUE INDEX uk_metric_dim ON ont_metric_dimension(metric_id, dimension_id);
