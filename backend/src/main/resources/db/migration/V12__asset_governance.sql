-- ============================================================================
-- 功能: P0-B asset catalog 治理整改迁移——资产组织/项目作用域、治理字段索引与受控标签兼容补齐。
--       V7 已创建 asset_tag 时仅补外键/索引/注释；缺失环境幂等创建。旧 asset.tags 保留为
--       legacy 兼容回显，新写路径由应用层使用 tagIds 写入 asset_tag。
-- 时间: 2026-07-01
-- 作者: AxeXie
-- ============================================================================

-- 资产表最小增量治理字段：接入组织/项目作用域，不破坏历史数据。
ALTER TABLE asset
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(40),
    ADD COLUMN IF NOT EXISTS project_id VARCHAR(40);

CREATE INDEX IF NOT EXISTS ix_asset_organization ON asset (organization_id);
CREATE INDEX IF NOT EXISTS ix_asset_project ON asset (project_id);
CREATE INDEX IF NOT EXISTS ix_asset_org_project_status ON asset (organization_id, project_id, status);
CREATE INDEX IF NOT EXISTS ix_asset_license ON asset (license);
-- model/dataset 治理字段存储在 asset_model/asset_dataset 扩展表，此处不建 JSONB 表达式索引。

COMMENT ON COLUMN asset.organization_id IS '资产所属组织 ID，可空表示历史/平台级资产；新写入资产按请求保存并参与授权过滤';
COMMENT ON COLUMN asset.project_id IS '资产所属项目 ID，可空表示未绑定项目；新写入资产按请求保存并参与授权过滤';
COMMENT ON COLUMN asset.tags IS '历史自由标签 JSONB，仅兼容回显与清点保留；新写入禁止自由标签，改用 asset_tag 受控 tagId 关联';
COMMENT ON COLUMN asset.owners IS 'Owner 主体/团队 ID JSONB；principal owner 新写入需存在，团队 owner 暂保留兼容校验策略';
COMMENT ON COLUMN asset.license IS '许可证字典 itemCode，历史值可回显；新写入必须引用 ACTIVE 字典项';
-- model/dataset 治理字段位于 asset_model/asset_dataset 扩展表，其 license/framework/task/format/modality
-- 新写入必须引用 ACTIVE 字典项；历史值可回显。

-- 若 V7 未执行过，幂等创建受控标签关联表；若已存在，则以下语句只补齐缺失结构。
CREATE TABLE IF NOT EXISTS asset_tag (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id        VARCHAR(40)  NOT NULL,
    tag_id          VARCHAR(40)  NOT NULL REFERENCES system_tag (tag_id) ON DELETE RESTRICT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_asset_tag UNIQUE (asset_id, tag_id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_asset_tag_asset'
    ) THEN
        ALTER TABLE asset_tag
            ADD CONSTRAINT fk_asset_tag_asset
            FOREIGN KEY (asset_id) REFERENCES asset (asset_id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_asset_tag_asset ON asset_tag (asset_id);
CREATE INDEX IF NOT EXISTS ix_asset_tag_tag ON asset_tag (tag_id);

COMMENT ON TABLE asset_tag IS '资产-受控标签关联表，承载新写路径 tagIds；停用标签历史关联保留可回显，不可新增引用';
COMMENT ON COLUMN asset_tag.id IS '内部自增主键，仅库内关联使用';
COMMENT ON COLUMN asset_tag.asset_id IS '资产业务 ID（ast_+ULID），外键引用 asset.asset_id，资产删除时级联清理关联';
COMMENT ON COLUMN asset_tag.tag_id IS '标签业务 ID，外键引用 system_tag.tag_id；应用层校验新增引用必须 ACTIVE';
COMMENT ON COLUMN asset_tag.created_at IS '关联建立时间（UTC）';

-- 存量 legacy tags 清点视图：用于治理对账，不参与新写入。
CREATE OR REPLACE VIEW asset_legacy_tag_inventory AS
SELECT asset_id,
       jsonb_array_elements_text(tags) AS legacy_tag,
       updated_at AS observed_at
FROM asset
WHERE jsonb_typeof(tags) = 'array'
  AND jsonb_array_length(tags) > 0;

COMMENT ON VIEW asset_legacy_tag_inventory IS '资产历史自由标签清点视图，仅用于兼容回显与治理对账；新写入使用 asset_tag.tag_id';

INSERT INTO iam_permission (permission_id, code, resource, action, description)
VALUES ('prm_asset_manage', 'asset:manage', 'asset', 'manage', '管理资产元数据（创建/更新/删除）')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_admin', permission_id FROM iam_permission WHERE code = 'asset:manage'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_asset_author', permission_id FROM iam_permission WHERE code = 'asset:manage'
ON CONFLICT (role_id, permission_id) DO NOTHING;
