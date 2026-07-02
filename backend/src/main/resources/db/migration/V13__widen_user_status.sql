-- ============================================================================
-- 功能: P0-B 修复迁移——放宽 iam_user.status 列宽以容纳 PENDING_ACTIVATION（18 字符）。
--       V3 中 iam_user.status 定义为 VARCHAR(16)，无法存放 'PENDING_ACTIVATION'，
--       导致 Bootstrap 管理员（首登强制改密，初始状态 PENDING_ACTIVATION）写入失败。
--       仅前向放宽列宽，不触碰 V1-V12，不改变 CHECK 约束语义。
-- 时间: 2026-07-02
-- 作者: AxeXie
-- ============================================================================

-- 放宽用户状态列宽（VARCHAR(16) -> VARCHAR(32)），容纳 PENDING_ACTIVATION 等较长稳定状态。
ALTER TABLE iam_user
    ALTER COLUMN status TYPE VARCHAR(32);

COMMENT ON COLUMN iam_user.status IS '用户状态：PENDING_ACTIVATION/ACTIVE/LOCKED/DISABLED（列宽 32）';
