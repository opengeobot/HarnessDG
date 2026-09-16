-- ============================================================================
-- 功能: 资产 Team Owner 约束——DEC-011 要求 Owner 至少一个 Team。
--       1. 存量数据修复：为无 Team 的资产设置 owner_team_id 或标记
--       2. 添加触发器阻止同时为空
-- 时间: 2026-07-30
-- 作者: AxeXie
-- ============================================================================

-- Step 1: 存量数据修复 —— 查找无 Team 的资产
-- 如果 team 表有数据，尝试给无 Team 资产分配第一个可用 Team
-- 如果没有 Team，标记为需修复

DO $$
DECLARE
    v_first_team_id VARCHAR(32);
    v_orphan_count INT;
BEGIN
    -- 获取第一个可用 Team（如有）
    SELECT team_id INTO v_first_team_id FROM team LIMIT 1;

    -- 统计无 Team 的资产
    SELECT COUNT(*) INTO v_orphan_count
    FROM asset
    WHERE owner_team_id IS NULL;

    IF v_first_team_id IS NOT NULL AND v_orphan_count > 0 THEN
        -- 将无 Team 的资产分配给第一个 Team
        UPDATE asset SET owner_team_id = v_first_team_id WHERE owner_team_id IS NULL;
        RAISE NOTICE 'Fixed % assets without team, assigned to %', v_orphan_count, v_first_team_id;
    ELSIF v_orphan_count > 0 THEN
        RAISE NOTICE 'WARNING: % assets without team and no teams exist. Skipping NOT NULL constraint.', v_orphan_count;
        RETURN;
    END IF;
END $$;

-- Step 2: 添加触发器阻止 owner_team_id 被设为 NULL 后保持 NULL
CREATE OR REPLACE FUNCTION enforce_asset_owner_team()
RETURNS TRIGGER AS $$
BEGIN
    -- 新创建时必须有 Team
    IF TG_OP = 'INSERT' AND NEW.owner_team_id IS NULL THEN
        RAISE EXCEPTION 'asset must have at least one owner team (owner_team_id cannot be NULL)';
    END IF;
    -- 更新时不允许将 Team 改为 NULL
    IF TG_OP = 'UPDATE' AND OLD.owner_team_id IS NOT NULL AND NEW.owner_team_id IS NULL THEN
        RAISE EXCEPTION 'asset owner_team_id cannot be set to NULL (DEC-011: at least one Team required)';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_enforce_asset_owner_team ON asset;
CREATE TRIGGER trg_enforce_asset_owner_team
    BEFORE INSERT OR UPDATE ON asset
    FOR EACH ROW
    EXECUTE FUNCTION enforce_asset_owner_team();

COMMENT ON FUNCTION enforce_asset_owner_team() IS 'DEC-011: 资产必须至少有一个 Owner Team，禁止 NULL';