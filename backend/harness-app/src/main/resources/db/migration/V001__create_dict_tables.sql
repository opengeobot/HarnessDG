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

COMMENT ON TABLE sys_dict_group IS '数据字典分组表';
COMMENT ON COLUMN sys_dict_group.id IS '主键ID';
COMMENT ON COLUMN sys_dict_group.code IS '分组编码（唯一）';
COMMENT ON COLUMN sys_dict_group.name IS '分组名称（多语言JSONB）';
COMMENT ON COLUMN sys_dict_group.description IS '分组描述（多语言JSONB）';
COMMENT ON COLUMN sys_dict_group.category IS '分类（business/system）';
COMMENT ON COLUMN sys_dict_group.is_tree IS '是否树形结构';
COMMENT ON COLUMN sys_dict_group.is_multiple IS '是否支持多选';
COMMENT ON COLUMN sys_dict_group.is_editable IS '是否可编辑';
COMMENT ON COLUMN sys_dict_group.status IS '状态（active/inactive）';
COMMENT ON COLUMN sys_dict_group.is_deleted IS '逻辑删除标识';
COMMENT ON COLUMN sys_dict_group.created_by IS '创建人';
COMMENT ON COLUMN sys_dict_group.updated_by IS '最后更新人';
COMMENT ON COLUMN sys_dict_group.created_at IS '创建时间';
COMMENT ON COLUMN sys_dict_group.updated_at IS '最后更新时间';

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

COMMENT ON TABLE sys_dict_item IS '数据字典项表';
COMMENT ON COLUMN sys_dict_item.id IS '主键ID';
COMMENT ON COLUMN sys_dict_item.group_code IS '所属分组编码';
COMMENT ON COLUMN sys_dict_item.parent_id IS '父项ID（树形结构）';
COMMENT ON COLUMN sys_dict_item.code IS '字典项编码（组内唯一）';
COMMENT ON COLUMN sys_dict_item.label IS '显示名称（多语言JSONB）';
COMMENT ON COLUMN sys_dict_item.description IS '描述（多语言JSONB）';
COMMENT ON COLUMN sys_dict_item.value IS '实际值';
COMMENT ON COLUMN sys_dict_item.icon IS '图标标识';
COMMENT ON COLUMN sys_dict_item.color IS '颜色代码';
COMMENT ON COLUMN sys_dict_item.sort_order IS '排序序号';
COMMENT ON COLUMN sys_dict_item.is_default IS '是否默认项';
COMMENT ON COLUMN sys_dict_item.is_system IS '是否系统内置（不可删除）';
COMMENT ON COLUMN sys_dict_item.status IS '状态（active/inactive）';
COMMENT ON COLUMN sys_dict_item.extra IS '扩展属性（JSONB）';
COMMENT ON COLUMN sys_dict_item.is_deleted IS '逻辑删除标识';
COMMENT ON COLUMN sys_dict_item.created_by IS '创建人';
COMMENT ON COLUMN sys_dict_item.updated_by IS '最后更新人';
COMMENT ON COLUMN sys_dict_item.created_at IS '创建时间';
COMMENT ON COLUMN sys_dict_item.updated_at IS '最后更新时间';

CREATE UNIQUE INDEX uk_dict_item_group_code ON sys_dict_item(group_code, code) WHERE is_deleted = FALSE;
CREATE INDEX idx_dict_item_group ON sys_dict_item(group_code) WHERE is_deleted = FALSE;
CREATE INDEX idx_dict_item_parent ON sys_dict_item(parent_id) WHERE parent_id IS NOT NULL AND is_deleted = FALSE;
