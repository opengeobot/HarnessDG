-- ============================================================================
-- 功能: P0-B taxonomy 字典与国际化迁移——可配置分类字典（类型/项）与多语言文案。
--       字典只存稳定的 item_code 与 i18n_key，绝不存展示文案；前端按 i18n_key 取文案展示。
--       停用项保留并可回显，但不可用于新建引用。字典类型携带缓存版本 version，任一字典项变更时
--       自增，供查询投影/前端缓存失效。稳定工作流状态仍以代码枚举 + 数据库约束承载，不进字典。
-- 时间: 2026-06-30
-- 作者: AxeXie
-- ============================================================================

-- 字典类型表：可配置分类的类型定义，dict_code 全局唯一；version 为缓存版本（字典项变更时自增）。
CREATE TABLE IF NOT EXISTS system_dict_type (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dict_type_id    VARCHAR(40)  NOT NULL UNIQUE,
    dict_code       VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    i18n_key        VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT       NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_system_dict_type_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

COMMENT ON TABLE system_dict_type IS '字典类型表，可配置分类的类型定义；dict_code 全局唯一；version 为缓存版本，字典项变更时自增';
COMMENT ON COLUMN system_dict_type.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN system_dict_type.dict_type_id IS '业务字典类型 ID（dct_+ULID 或稳定派生键）';
COMMENT ON COLUMN system_dict_type.dict_code IS '字典编码（如 model_task/license_catalog），全局唯一';
COMMENT ON COLUMN system_dict_type.name IS '字典类型名称（管理后台展示用，非业务文案来源）';
COMMENT ON COLUMN system_dict_type.i18n_key IS '国际化键，前端据此从 system_i18n_message 取展示文案';
COMMENT ON COLUMN system_dict_type.description IS '字典类型说明（可空）';
COMMENT ON COLUMN system_dict_type.status IS '状态：ACTIVE 启用 / DISABLED 停用';
COMMENT ON COLUMN system_dict_type.version IS '缓存版本号，任一字典项新增/修改/停用时自增，供前端/投影缓存失效';
COMMENT ON COLUMN system_dict_type.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN system_dict_type.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN system_dict_type.row_version IS '乐观锁版本号（字典类型自身编辑保留列）';

-- 字典项表：仅存 item_code + i18n_key，不存展示文案；(dict_code, item_code) 唯一。
CREATE TABLE IF NOT EXISTS system_dict_item (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dict_item_id    VARCHAR(40)  NOT NULL UNIQUE,
    dict_code       VARCHAR(64)  NOT NULL REFERENCES system_dict_type (dict_code) ON DELETE CASCADE,
    item_code       VARCHAR(64)  NOT NULL,
    i18n_key        VARCHAR(128) NOT NULL,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    extra           JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 1,
    row_version     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_system_dict_item_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ux_system_dict_item UNIQUE (dict_code, item_code)
);

CREATE INDEX IF NOT EXISTS ix_system_dict_item_dict_code ON system_dict_item (dict_code);

COMMENT ON TABLE system_dict_item IS '字典项表，仅存 item_code 与 i18n_key（不存展示文案）；(dict_code, item_code) 唯一';
COMMENT ON COLUMN system_dict_item.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN system_dict_item.dict_item_id IS '业务字典项 ID（dct_+稳定派生键）';
COMMENT ON COLUMN system_dict_item.dict_code IS '所属字典编码，外键引用 system_dict_type.dict_code';
COMMENT ON COLUMN system_dict_item.item_code IS '字典项编码（治理字段实际持久化的稳定值，如 APACHE_2_0）';
COMMENT ON COLUMN system_dict_item.i18n_key IS '国际化键，前端据此取展示文案';
COMMENT ON COLUMN system_dict_item.sort_order IS '排序序号，越小越靠前';
COMMENT ON COLUMN system_dict_item.status IS '状态：ACTIVE 启用（可新建引用）/ DISABLED 停用（不可新建引用，仅回显）';
COMMENT ON COLUMN system_dict_item.extra IS '扩展属性（JSONB，可空）';
COMMENT ON COLUMN system_dict_item.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN system_dict_item.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN system_dict_item.version IS '语义版本号，新增为 1，每次更新自增；对外作为 version 暴露并用于 expectedVersion 乐观并发校验';
COMMENT ON COLUMN system_dict_item.row_version IS '乐观锁版本号（保留列）';

-- 国际化文案表：locale + message_key 唯一，承载 i18n_key→文案映射。
CREATE TABLE IF NOT EXISTS system_i18n_message (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    locale          VARCHAR(16)  NOT NULL,
    message_key     VARCHAR(128) NOT NULL,
    message         VARCHAR(512) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_system_i18n_message_locale CHECK (locale IN ('zh-CN', 'en-US')),
    CONSTRAINT ux_system_i18n_message UNIQUE (locale, message_key)
);

CREATE INDEX IF NOT EXISTS ix_system_i18n_message_locale ON system_i18n_message (locale);

COMMENT ON TABLE system_i18n_message IS '国际化文案表，承载 i18n_key→文案映射；(locale, message_key) 唯一';
COMMENT ON COLUMN system_i18n_message.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN system_i18n_message.locale IS '语言区域：zh-CN 简体中文 / en-US 英文';
COMMENT ON COLUMN system_i18n_message.message_key IS '国际化键，对应字典/标签等的 i18n_key';
COMMENT ON COLUMN system_i18n_message.message IS '该语言下的展示文案';
COMMENT ON COLUMN system_i18n_message.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN system_i18n_message.updated_at IS '更新时间（UTC）';

-- ----------------------------------------------------------------------------
-- 预置字典类型。dict_type_id 采用稳定派生键，便于幂等重放。
-- ----------------------------------------------------------------------------
INSERT INTO system_dict_type (dict_type_id, dict_code, name, i18n_key, description)
SELECT 'dct_' || dict_code, dict_code, name, 'dict.' || dict_code, descr
FROM (VALUES
    ('model_task',        '模型任务',     '模型支持的任务类型'),
    ('model_framework',   '模型框架',     '模型训练/推理框架'),
    ('dataset_modality',  '数据集模态',   '数据集模态分类'),
    ('dataset_format',    '数据集格式',   '数据集文件格式'),
    ('industry_tag',      '行业分类',     '资产适用行业分类'),
    ('license_catalog',   '许可证目录',   '资产许可证目录'),
    ('sensitivity_level', '敏感级别',     '资产数据敏感级别'),
    ('deprecation_reason','弃用原因',     '资产/版本弃用原因')
) AS seed(dict_code, name, descr)
ON CONFLICT (dict_code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 预置字典项。dict_item_id 采用 dct_<dict_code>_<item_code> 稳定派生键；i18n_key 为 dict.<dict_code>.<item_code>。
-- ----------------------------------------------------------------------------
INSERT INTO system_dict_item (dict_item_id, dict_code, item_code, i18n_key, sort_order)
SELECT 'dct_' || dict_code || '_' || item_code,
       dict_code,
       item_code,
       'dict.' || dict_code || '.' || item_code,
       sort_order
FROM (VALUES
    ('license_catalog',   'APACHE_2_0',          0),
    ('license_catalog',   'MIT',                 1),
    ('license_catalog',   'CC_BY_4_0',           2),
    ('model_framework',   'PYTORCH',             0),
    ('model_framework',   'TENSORFLOW',          1),
    ('model_framework',   'PADDLE',              2),
    ('model_task',        'TEXT_GENERATION',     0),
    ('model_task',        'IMAGE_CLASSIFICATION',1),
    ('model_task',        'OBJECT_DETECTION',    2),
    ('dataset_modality',  'TEXT',                0),
    ('dataset_modality',  'IMAGE',               1),
    ('dataset_modality',  'AUDIO',               2),
    ('dataset_modality',  'VIDEO',               3),
    ('dataset_modality',  'TABULAR',             4),
    ('dataset_format',    'JSONL',               0),
    ('dataset_format',    'CSV',                 1),
    ('dataset_format',    'PARQUET',             2),
    ('dataset_format',    'IMAGE_FOLDER',        3),
    ('sensitivity_level', 'PUBLIC',              0),
    ('sensitivity_level', 'INTERNAL',            1),
    ('sensitivity_level', 'CONFIDENTIAL',        2),
    ('sensitivity_level', 'SECRET',              3)
) AS seed(dict_code, item_code, sort_order)
ON CONFLICT (dict_code, item_code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 预置国际化文案（zh-CN / en-US），覆盖字典类型与字典项的 i18n_key。
-- ----------------------------------------------------------------------------
INSERT INTO system_i18n_message (locale, message_key, message)
SELECT locale, message_key, message
FROM (VALUES
    -- 字典类型
    ('zh-CN', 'dict.model_task',        '模型任务'),
    ('en-US', 'dict.model_task',        'Model Task'),
    ('zh-CN', 'dict.model_framework',   '模型框架'),
    ('en-US', 'dict.model_framework',   'Model Framework'),
    ('zh-CN', 'dict.dataset_modality',  '数据集模态'),
    ('en-US', 'dict.dataset_modality',  'Dataset Modality'),
    ('zh-CN', 'dict.dataset_format',    '数据集格式'),
    ('en-US', 'dict.dataset_format',    'Dataset Format'),
    ('zh-CN', 'dict.industry_tag',      '行业分类'),
    ('en-US', 'dict.industry_tag',      'Industry'),
    ('zh-CN', 'dict.license_catalog',   '许可证目录'),
    ('en-US', 'dict.license_catalog',   'License Catalog'),
    ('zh-CN', 'dict.sensitivity_level', '敏感级别'),
    ('en-US', 'dict.sensitivity_level', 'Sensitivity Level'),
    ('zh-CN', 'dict.deprecation_reason','弃用原因'),
    ('en-US', 'dict.deprecation_reason','Deprecation Reason'),
    -- license_catalog 项
    ('zh-CN', 'dict.license_catalog.APACHE_2_0', 'Apache 许可证 2.0'),
    ('en-US', 'dict.license_catalog.APACHE_2_0', 'Apache License 2.0'),
    ('zh-CN', 'dict.license_catalog.MIT',        'MIT 许可证'),
    ('en-US', 'dict.license_catalog.MIT',        'MIT License'),
    ('zh-CN', 'dict.license_catalog.CC_BY_4_0',  '知识共享署名 4.0'),
    ('en-US', 'dict.license_catalog.CC_BY_4_0',  'CC BY 4.0'),
    -- model_framework 项
    ('zh-CN', 'dict.model_framework.PYTORCH',    'PyTorch'),
    ('en-US', 'dict.model_framework.PYTORCH',    'PyTorch'),
    ('zh-CN', 'dict.model_framework.TENSORFLOW', 'TensorFlow'),
    ('en-US', 'dict.model_framework.TENSORFLOW', 'TensorFlow'),
    ('zh-CN', 'dict.model_framework.PADDLE',     'PaddlePaddle'),
    ('en-US', 'dict.model_framework.PADDLE',     'PaddlePaddle'),
    -- model_task 项
    ('zh-CN', 'dict.model_task.TEXT_GENERATION',      '文本生成'),
    ('en-US', 'dict.model_task.TEXT_GENERATION',      'Text Generation'),
    ('zh-CN', 'dict.model_task.IMAGE_CLASSIFICATION', '图像分类'),
    ('en-US', 'dict.model_task.IMAGE_CLASSIFICATION', 'Image Classification'),
    ('zh-CN', 'dict.model_task.OBJECT_DETECTION',     '目标检测'),
    ('en-US', 'dict.model_task.OBJECT_DETECTION',     'Object Detection'),
    -- dataset_modality 项
    ('zh-CN', 'dict.dataset_modality.TEXT',    '文本'),
    ('en-US', 'dict.dataset_modality.TEXT',    'Text'),
    ('zh-CN', 'dict.dataset_modality.IMAGE',   '图像'),
    ('en-US', 'dict.dataset_modality.IMAGE',   'Image'),
    ('zh-CN', 'dict.dataset_modality.AUDIO',   '音频'),
    ('en-US', 'dict.dataset_modality.AUDIO',   'Audio'),
    ('zh-CN', 'dict.dataset_modality.VIDEO',   '视频'),
    ('en-US', 'dict.dataset_modality.VIDEO',   'Video'),
    ('zh-CN', 'dict.dataset_modality.TABULAR', '表格'),
    ('en-US', 'dict.dataset_modality.TABULAR', 'Tabular'),
    -- dataset_format 项
    ('zh-CN', 'dict.dataset_format.JSONL',        'JSON Lines'),
    ('en-US', 'dict.dataset_format.JSONL',        'JSON Lines'),
    ('zh-CN', 'dict.dataset_format.CSV',          'CSV'),
    ('en-US', 'dict.dataset_format.CSV',          'CSV'),
    ('zh-CN', 'dict.dataset_format.PARQUET',      'Parquet'),
    ('en-US', 'dict.dataset_format.PARQUET',      'Parquet'),
    ('zh-CN', 'dict.dataset_format.IMAGE_FOLDER', '图像文件夹'),
    ('en-US', 'dict.dataset_format.IMAGE_FOLDER', 'Image Folder'),
    -- sensitivity_level 项
    ('zh-CN', 'dict.sensitivity_level.PUBLIC',       '公开'),
    ('en-US', 'dict.sensitivity_level.PUBLIC',       'Public'),
    ('zh-CN', 'dict.sensitivity_level.INTERNAL',     '内部'),
    ('en-US', 'dict.sensitivity_level.INTERNAL',     'Internal'),
    ('zh-CN', 'dict.sensitivity_level.CONFIDENTIAL', '机密'),
    ('en-US', 'dict.sensitivity_level.CONFIDENTIAL', 'Confidential'),
    ('zh-CN', 'dict.sensitivity_level.SECRET',       '绝密'),
    ('en-US', 'dict.sensitivity_level.SECRET',       'Secret')
) AS seed(locale, message_key, message)
ON CONFLICT (locale, message_key) DO NOTHING;
