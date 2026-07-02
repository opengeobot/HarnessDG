-- ============================================================================
-- 功能: P0-B configuration 统一配置迁移——system_config 承载运行配置的类型/默认值/校验器/
--       作用域/是否热更新等元数据。密码/Token/私钥等敏感项绝不写入本表（应用层 Secret 模式拒绝）。
--       配置变更带版本自增与审计；不可热更新项需提示重启。类型安全读取由 PlatformConfigService 提供。
-- 时间: 2026-06-30
-- 作者: AxeXie
-- ============================================================================

-- 统一配置表：运行配置元数据 + 当前值；config_key 全局唯一。
CREATE TABLE IF NOT EXISTS system_config (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    config_id       VARCHAR(40)  NOT NULL UNIQUE,
    config_key      VARCHAR(128) NOT NULL UNIQUE,
    value_type      VARCHAR(16)  NOT NULL,
    config_value    TEXT,
    default_value   TEXT,
    scope_type      VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM',
    scope_id        VARCHAR(40),
    hot_reloadable  SMALLINT     NOT NULL DEFAULT 1,
    validator       VARCHAR(255),
    description     VARCHAR(512),
    version         BIGINT       NOT NULL DEFAULT 1,
    updated_by      VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_system_config_value_type CHECK (
        value_type IN ('STRING', 'INTEGER', 'LONG', 'BOOLEAN', 'DURATION', 'JSON')),
    CONSTRAINT ck_system_config_scope_type CHECK (scope_type IN ('PLATFORM', 'ORGANIZATION', 'PROJECT')),
    CONSTRAINT ck_system_config_hot_reloadable CHECK (hot_reloadable IN (0, 1))
);

COMMENT ON TABLE system_config IS '统一配置表，承载运行配置的类型/默认值/校验器/作用域/热更新元数据；敏感项绝不入表';
COMMENT ON COLUMN system_config.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN system_config.config_id IS '业务配置 ID（cfg_+稳定派生键）';
COMMENT ON COLUMN system_config.config_key IS '配置键（如 transfer.web.maxSessionBytes），全局唯一';
COMMENT ON COLUMN system_config.value_type IS '值类型：STRING/INTEGER/LONG/BOOLEAN/DURATION/JSON，用于类型安全读取与提交校验';
COMMENT ON COLUMN system_config.config_value IS '当前值（文本序列化，按 value_type 解析）；为空时回退 default_value';
COMMENT ON COLUMN system_config.default_value IS '默认值（文本序列化）';
COMMENT ON COLUMN system_config.scope_type IS '作用域类型：PLATFORM/ORGANIZATION/PROJECT，P0-B 仅平台作用域';
COMMENT ON COLUMN system_config.scope_id IS '作用域 ID（组织/项目），平台作用域为空';
COMMENT ON COLUMN system_config.hot_reloadable IS '是否可热更新：1 热更新生效 / 0 需重启生效（更新时提示）';
COMMENT ON COLUMN system_config.validator IS '校验器描述或正则（可空），更新提交值时辅助校验';
COMMENT ON COLUMN system_config.description IS '配置说明（可空）';
COMMENT ON COLUMN system_config.version IS '语义版本号，新增为 1，每次更新自增；对外暴露并用于 expectedVersion 乐观并发校验';
COMMENT ON COLUMN system_config.updated_by IS '最近更新者主体 ID';
COMMENT ON COLUMN system_config.created_at IS '创建时间（UTC）';
COMMENT ON COLUMN system_config.updated_at IS '更新时间（UTC）';
COMMENT ON COLUMN system_config.row_version IS '乐观锁版本号（保留列）';

-- ----------------------------------------------------------------------------
-- 预置非敏感运行配置默认值。config_id 采用稳定派生键便于幂等重放。
-- 绝不预置任何密码/Token/私钥/凭据类配置。
-- ----------------------------------------------------------------------------
INSERT INTO system_config (config_id, config_key, value_type, config_value, default_value,
                           hot_reloadable, validator, description)
SELECT 'cfg_' || replace(config_key, '.', '_'),
       config_key, value_type, default_value, default_value, hot_reloadable, validator, descr
FROM (VALUES
    ('transfer.web.maxSessionBytes',     'LONG',    '5368709120', 1, '>0',          'Web 直传单会话最大字节数'),
    ('transfer.presignedUrl.ttlSeconds', 'INTEGER', '900',        1, '60..3600',    '预签名 URL 有效期（秒）'),
    ('version.publish.requireApproval',  'BOOLEAN', 'true',       0, 'true|false',  '发布版本是否要求审批（不可热更新，变更需重启）'),
    ('job.dvc.maxRetries',               'INTEGER', '5',          1, '0..20',       'DVC 任务最大重试次数'),
    ('job.webhook.maxRetries',           'INTEGER', '8',          1, '0..20',       'Webhook 任务最大重试次数'),
    ('mcp.maxResultItems',               'INTEGER', '50',         1, '1..500',      'MCP 工具单次返回最大条目数'),
    ('mcp.writeTools.enabled',           'BOOLEAN', 'false',      0, 'true|false',  '是否启用 MCP 写工具（不可热更新，变更需重启）'),
    ('security.agentToken.maxTtlSeconds','INTEGER', '3600',       0, '300..86400',  'Agent 令牌最大有效期（秒，安全项需重启）'),
    ('audit.retentionDays',              'INTEGER', '180',        1, '30..3650',    '审计日志保留天数'),
    ('notification.webhook.enabled',     'BOOLEAN', 'true',       1, 'true|false',  '是否启用 Webhook 通知')
) AS seed(config_key, value_type, default_value, hot_reloadable, validator, descr)
ON CONFLICT (config_key) DO NOTHING;
