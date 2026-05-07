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

CREATE UNIQUE INDEX uk_metric_dim ON ont_metric_dimension(metric_id, dimension_id);
