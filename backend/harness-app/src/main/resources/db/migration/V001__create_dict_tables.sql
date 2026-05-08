-- V001__create_dict_tables.sql
-- 功能：创建统一数据字典表（字典分组 + 字典项）
-- 时间：2026-05-07
-- 作者：AxeXie

-- 字典分组表
CREATE TABLE sys_dict_group (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(100) NOT NULL,
    name          JSONB NOT NULL,
    description   JSONB,
    category      VARCHAR(50) NOT NULL DEFAULT 'business',
    is_tree       BOOLEAN NOT NULL DEFAULT FALSE,
    is_multiple   BOOLEAN NOT NULL DEFAULT FALSE,
    is_editable   BOOLEAN NOT NULL DEFAULT TRUE,
    status        VARCHAR(20) NOT NULL DEFAULT 'active',
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    created_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_dict_group_code ON sys_dict_group(code) WHERE is_deleted = FALSE;

-- 字典项表
CREATE TABLE sys_dict_item (
    id            BIGSERIAL PRIMARY KEY,
    group_code    VARCHAR(100) NOT NULL,
    parent_id     BIGINT,
    code          VARCHAR(100) NOT NULL,
    label         JSONB NOT NULL,
    description   JSONB,
    value         VARCHAR(255) NOT NULL,
    icon          VARCHAR(100),
    color         VARCHAR(20),
    sort_order    INT NOT NULL DEFAULT 0,
    is_default    BOOLEAN NOT NULL DEFAULT FALSE,
    is_system     BOOLEAN NOT NULL DEFAULT FALSE,
    status        VARCHAR(20) NOT NULL DEFAULT 'active',
    extra         JSONB,
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    created_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_dict_item_group_code ON sys_dict_item(group_code, code) WHERE is_deleted = FALSE;
CREATE INDEX idx_dict_item_group ON sys_dict_item(group_code) WHERE is_deleted = FALSE;
CREATE INDEX idx_dict_item_parent ON sys_dict_item(parent_id) WHERE parent_id IS NOT NULL AND is_deleted = FALSE;
