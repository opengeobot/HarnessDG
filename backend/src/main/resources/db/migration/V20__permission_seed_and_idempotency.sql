-- V20: 权限 seed 对齐 + 幂等表增强
-- 依据: AUD-006 (幂等性全路径接入), AUD-016 (权限常量缺失), AUD-017 (READER 角色过宽)

-- ----------------------------------------------------------------------------
-- 1. api_idempotency 增加 principal_id 列（审计用途）
-- ----------------------------------------------------------------------------
ALTER TABLE api_idempotency ADD COLUMN IF NOT EXISTS principal_id VARCHAR(64);
COMMENT ON COLUMN api_idempotency.principal_id IS '发起主体 ID，用于审计与多主体隔离';

-- ----------------------------------------------------------------------------
-- 2. 补全 Permissions.java 中存在但 V4 seed 缺失的权限
-- ----------------------------------------------------------------------------
INSERT INTO iam_permission (permission_id, code, resource, action, description)
SELECT 'prm_' || replace(code, ':', '_'),
       code,
       split_part(code, ':', 1),
       split_part(code, ':', 2),
       descr
FROM (VALUES
    ('team:manage',     '管理团队'),
    ('asset:manage',    '管理资产（综合）'),
    ('asset:discuss',   '参与资产讨论'),
    ('asset:moderate',  '管理资产讨论（Moderator）')
) AS seed(code, descr)
ON CONFLICT (code) DO NOTHING;

-- ADMIN 角色绑定新增权限。
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_admin', permission_id
FROM iam_permission
WHERE code IN ('team:manage', 'asset:manage', 'asset:discuss', 'asset:moderate')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ASSET_AUTHOR 绑定 asset:discuss（作者可参与讨论）。
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_asset_author', permission_id
FROM iam_permission
WHERE code IN ('asset:discuss')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3. READER 角色收窄：移除管理/诊断类权限
--    保留: project:view, user:read, dictionary:read, tag:read, asset:read, asset:download
--    移除: authorization:read, audit:read, job:read, system:observe, notification:read
-- ----------------------------------------------------------------------------
DELETE FROM iam_role_permission
WHERE role_id = 'rol_reader'
  AND permission_id IN (
    SELECT permission_id FROM iam_permission
    WHERE code IN ('authorization:read', 'audit:read', 'job:read',
                   'system:observe', 'notification:read')
  );
