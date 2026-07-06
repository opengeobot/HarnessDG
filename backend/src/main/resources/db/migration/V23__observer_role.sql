-- ============================================================================
-- 功能: TASK-P0BR-004 — 按 DEC-018 拆分 READER 角色为普通用户与审计/运维。
--       READER（普通用户 PER-007）：7 项权限
--       OBSERVER（审计/运维 PER-011）：继承 READER 全部 + 5 项只读 = 12 项权限
--       ADMIN 角色不受影响（已绑定全部权限）。
-- 时间: 2026-07-06
-- 作者: AxeXie
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 创建 OBSERVER 内置角色
-- ----------------------------------------------------------------------------
INSERT INTO iam_role (role_id, code, name, description, scope_type, builtin, status)
VALUES ('rol_observer', 'OBSERVER', '审计/运维人员',
        '审计和运维只读角色，可查看审计、任务、系统状态等诊断信息',
        'PLATFORM', 1, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. READER 角色对齐 DEC-018（7 项权限）
--    当前（V4+V14+V20 后）READER 拥有:
--      project:view, user:read, dictionary:read, tag:read,
--      asset:read, asset:download, asset:discuss
--    目标:
--      project:view, dictionary:read, tag:read, asset:read,
--      asset:download, asset:discuss, notification:read
--    变更: 移除 user:read（属于 OBSERVER），恢复 notification:read
-- ----------------------------------------------------------------------------

-- 移除 user:read（READER 不应查看用户管理信息，属于 OBSERVER 权限）
DELETE FROM iam_role_permission
WHERE role_id = 'rol_reader'
  AND permission_id = (SELECT permission_id FROM iam_permission WHERE code = 'user:read');

-- 恢复 notification:read（V20 误删，DEC-018 明确 READER 需要此权限）
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_reader', permission_id FROM iam_permission WHERE code = 'notification:read'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3. OBSERVER 绑定 12 项权限（READER 的 7 项 + 5 项额外只读）
-- ----------------------------------------------------------------------------
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_observer', permission_id FROM iam_permission
WHERE code IN (
    -- READER 的 7 项权限
    'project:view', 'dictionary:read', 'tag:read',
    'asset:read', 'asset:download', 'asset:discuss', 'notification:read',
    -- OBSERVER 额外的 5 项只读权限
    'user:read', 'authorization:read', 'audit:read', 'job:read', 'system:observe'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;
