-- V009__seed_system_config.sql
-- 功能：初始化系统配置
-- 时间：2026-05-07
-- 作者：AxeXie

INSERT INTO sys_config (config_key, config_value, value_type, category, description, environment) VALUES
-- Agent 配置
('agent.qwenpaw.endpoint', 'http://localhost:8088', 'string', 'agent', '{"zh_CN":"QwenPaw服务地址","en_US":"QwenPaw service endpoint"}', 'all'),
('agent.qwenpaw.timeout_ms', '30000', 'number', 'agent', '{"zh_CN":"Agent请求超时(毫秒)","en_US":"Agent request timeout (ms)"}', 'all'),
('agent.qwenpaw.max_retries', '2', 'number', 'agent', '{"zh_CN":"Agent最大重试次数","en_US":"Agent max retry count"}', 'all'),

-- 安全配置
('security.jwt.expiry_minutes', '60', 'number', 'security', '{"zh_CN":"JWT过期时间(分钟)","en_US":"JWT expiry time (minutes)"}', 'all'),
('security.jwt.refresh_expiry_days', '7', 'number', 'security', '{"zh_CN":"Refresh Token过期时间(天)","en_US":"Refresh token expiry (days)"}', 'all'),

-- 功能开关
('feature.ai_explain.enabled', 'true', 'boolean', 'feature', '{"zh_CN":"是否启用AI结果解释","en_US":"Enable AI result explanation"}', 'all'),
('feature.auto_heal.enabled', 'false', 'boolean', 'feature', '{"zh_CN":"是否启用异常自愈","en_US":"Enable auto healing for exceptions"}', 'all'),

-- 字典配置
('dictionary.cache_ttl_minutes', '5', 'number', 'system', '{"zh_CN":"字典缓存TTL(分钟)","en_US":"Dictionary cache TTL (minutes)"}', 'all'),

-- Dagster 配置
('dagster.graphql_url', 'http://localhost:3000/graphql', 'string', 'schedule', '{"zh_CN":"Dagster GraphQL地址","en_US":"Dagster GraphQL endpoint"}', 'all');
