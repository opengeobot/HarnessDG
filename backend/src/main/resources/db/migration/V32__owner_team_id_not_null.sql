-- ============================================================================
-- 功能: Owner Team 治理闭环——回填 NULL owner_team_id 并强制 NOT NULL。
-- 依据: PRD Owner/tag 治理；DEC-011
-- 时间: 2026-07-11
-- 作者: AxeXie
-- ============================================================================

-- 平台占位组织（承载未映射资产的主 Owner 团队）。
INSERT INTO organization (organization_id, code, name, description, status, created_by)
VALUES ('org_unmapped', 'unmapped', 'Unmapped Assets', 'Placeholder org for legacy assets without owner team', 'ACTIVE', 'system')
ON CONFLICT (organization_id) DO NOTHING;

-- 占位团队：历史资产无 owner_team_id 时回填此标记。
INSERT INTO team (team_id, organization_id, name, description, status, created_by)
VALUES ('unmapped_team', 'org_unmapped', 'Unmapped Team', 'Placeholder team for legacy assets', 'ACTIVE', 'system')
ON CONFLICT (team_id) DO NOTHING;

-- 回填 NULL owner_team_id。
UPDATE asset
SET owner_team_id = 'unmapped_team',
    updated_at = now()
WHERE owner_team_id IS NULL
  AND deleted = 0;

-- 强制 NOT NULL（新写入必须提供 ownerTeamId）。
ALTER TABLE asset ALTER COLUMN owner_team_id SET NOT NULL;

COMMENT ON COLUMN asset.owner_team_id IS '主 Owner 团队 ID（FK→team.team_id），DEC-011 要求必填';
