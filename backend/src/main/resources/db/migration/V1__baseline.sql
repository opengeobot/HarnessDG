-- ============================================================================
-- 功能: P0 工程基线迁移——仅登记平台 Schema 基线信息，不创建任何业务表。
-- 时间: 2026-06-29
-- 作者: AxeXie
-- ============================================================================

-- 平台 Schema 基线信息表：记录基线版本与说明，用于确认迁移管线已就绪。
CREATE TABLE IF NOT EXISTS platform_schema_info (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    schema_key  VARCHAR(64)  NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 表注释
COMMENT ON TABLE platform_schema_info IS '平台 Schema 基线信息表，记录 P0 工程基线的迁移就绪标记，不承载业务数据';
-- 字段注释
COMMENT ON COLUMN platform_schema_info.id IS '内部自增主键，仅库内关联使用，不作为跨系统标识';
COMMENT ON COLUMN platform_schema_info.schema_key IS '基线信息键，全局唯一';
COMMENT ON COLUMN platform_schema_info.description IS '基线信息描述';
COMMENT ON COLUMN platform_schema_info.created_at IS '记录创建时间（带时区）';

-- 写入基线标记
INSERT INTO platform_schema_info (schema_key, description)
VALUES ('baseline', 'P0 工程基线已建立，业务表由后续前向迁移引入')
ON CONFLICT (schema_key) DO NOTHING;
