-- ============================================================================
-- 功能: P1 资产目录整改 + P0-B 权限漂移修复。
--       Part A: 修复 V4 权限种子漂移——补录 asset:manage / asset:discuss / asset:moderate，
--               并将新权限授予 ASSET_AUTHOR 内置角色。
--       Part B: 资产 Schema 整改——扩展治理字段、启用 pg_trgm 全文索引。
-- 时间: 2026-07-02
-- 作者: AxeXie
-- ============================================================================

-- ===========================================================================
-- Part A: P0-B 权限种子漂移修复
-- ===========================================================================

-- 补录 P1 Discussion 子系统所需权限定义。
-- 注：asset:manage 已由 V12 补录，此处仅补录 asset:discuss / asset:moderate。
INSERT INTO iam_permission (permission_id, code, resource, action, description)
SELECT 'prm_asset_discuss', 'asset:discuss', 'asset', 'discuss', '参与资产讨论（创建 Thread/回复/Mention）'
WHERE NOT EXISTS (SELECT 1 FROM iam_permission WHERE code = 'asset:discuss');

INSERT INTO iam_permission (permission_id, code, resource, action, description)
SELECT 'prm_asset_moderate', 'asset:moderate', 'asset', 'moderate', '管理资产讨论（Moderator 隐藏/恢复/锁定）'
WHERE NOT EXISTS (SELECT 1 FROM iam_permission WHERE code = 'asset:moderate');

-- 将 asset:discuss 授予 ASSET_AUTHOR 角色。
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_asset_author', permission_id FROM iam_permission WHERE code = 'asset:discuss'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 将 asset:discuss / asset:moderate 授予 ADMIN 角色（ADMIN 绑定全部权限）。
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_admin', permission_id FROM iam_permission
WHERE code IN ('asset:discuss', 'asset:moderate')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 将 asset:discuss 授予 READER 角色（只读用户可参与讨论）。
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT 'rol_reader', permission_id FROM iam_permission WHERE code = 'asset:discuss'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ===========================================================================
-- Part B: 资产 Schema 整改
-- ===========================================================================

-- 启用 pg_trgm 扩展（用于 name/display_name 的 GIN 三字母组索引）。
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---- asset 表新增字段 ----

-- 建仓异步状态（Saga 驱动，替代同步建仓）。
ALTER TABLE asset ADD COLUMN IF NOT EXISTS provisioning_status VARCHAR(16) DEFAULT 'NONE';
ALTER TABLE asset ADD CONSTRAINT ck_asset_provisioning_status
    CHECK (provisioning_status IN ('NONE', 'PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'));

COMMENT ON COLUMN asset.provisioning_status IS '建仓异步状态：NONE 未触发 / PENDING 等待 / IN_PROGRESS 执行中 / COMPLETED 完成 / FAILED 失败';

-- Card 投影（从 Gitea 锁定 Commit 读取，带 sourceCommit 溯源，不存独立权威副本）。
ALTER TABLE asset ADD COLUMN IF NOT EXISTS source_commit VARCHAR(64);
ALTER TABLE asset ADD COLUMN IF NOT EXISTS card_readme TEXT;
ALTER TABLE asset ADD COLUMN IF NOT EXISTS card_asset_yaml TEXT;

COMMENT ON COLUMN asset.source_commit IS 'Card 投影来源的 Gitea Commit SHA（可空，空表示未投影）';
COMMENT ON COLUMN asset.card_readme IS '从 Gitea 投影的 README.md 内容（untrustedContent，前端需 DOMPurify 渲染）';
COMMENT ON COLUMN asset.card_asset_yaml IS '从 Gitea 投影的 asset.yaml 内容（untrustedContent）';

-- ---- asset_model 表扩展字段 ----

ALTER TABLE asset_model ADD COLUMN IF NOT EXISTS parameter_scale VARCHAR(64);
ALTER TABLE asset_model ADD COLUMN IF NOT EXISTS precision VARCHAR(32);
ALTER TABLE asset_model ADD COLUMN IF NOT EXISTS weight_format VARCHAR(32);
ALTER TABLE asset_model ADD COLUMN IF NOT EXISTS runtime VARCHAR(64);
ALTER TABLE asset_model ADD COLUMN IF NOT EXISTS known_risks JSONB;
ALTER TABLE asset_model ADD COLUMN IF NOT EXISTS usage_restrictions JSONB;
ALTER TABLE asset_model ADD COLUMN IF NOT EXISTS sensitivity_code VARCHAR(64);

COMMENT ON COLUMN asset_model.parameter_scale IS '参数规模（如 7B/13B/70B）';
COMMENT ON COLUMN asset_model.precision IS '精度（如 fp16/bf16/int8）';
COMMENT ON COLUMN asset_model.weight_format IS '权重格式（如 safetensors/gguf/bin）';
COMMENT ON COLUMN asset_model.runtime IS '运行时（如 onnx/vllm/triton）';
COMMENT ON COLUMN asset_model.known_risks IS '已知风险（JSON 数组）';
COMMENT ON COLUMN asset_model.usage_restrictions IS '使用限制（JSON 数组）';
COMMENT ON COLUMN asset_model.sensitivity_code IS '敏感级别编码（字典 sensitivity_level 的有效 itemCode）';

-- ---- asset_dataset 表扩展字段 ----
-- 注：V2 的 format/modality 列保留不删，新列全部可空。

ALTER TABLE asset_dataset ADD COLUMN IF NOT EXISTS task_codes JSONB;
ALTER TABLE asset_dataset ADD COLUMN IF NOT EXISTS modality_codes JSONB;
ALTER TABLE asset_dataset ADD COLUMN IF NOT EXISTS format_codes JSONB;
ALTER TABLE asset_dataset ADD COLUMN IF NOT EXISTS language_codes JSONB;
ALTER TABLE asset_dataset ADD COLUMN IF NOT EXISTS sensitivity_code VARCHAR(64);
ALTER TABLE asset_dataset ADD COLUMN IF NOT EXISTS sample_count BIGINT;
ALTER TABLE asset_dataset ADD COLUMN IF NOT EXISTS total_bytes BIGINT;
ALTER TABLE asset_dataset ADD COLUMN IF NOT EXISTS size_bucket_code VARCHAR(64);

COMMENT ON COLUMN asset_dataset.task_codes IS '任务编码列表（JSON 数组，model_task 字典多值）';
COMMENT ON COLUMN asset_dataset.modality_codes IS '模态编码列表（JSON 数组，dataset_modality 字典多值）';
COMMENT ON COLUMN asset_dataset.format_codes IS '格式编码列表（JSON 数组，dataset_format 字典多值）';
COMMENT ON COLUMN asset_dataset.language_codes IS '语言编码列表（JSON 数组）';
COMMENT ON COLUMN asset_dataset.sensitivity_code IS '敏感级别编码';
COMMENT ON COLUMN asset_dataset.sample_count IS '样本数量';
COMMENT ON COLUMN asset_dataset.total_bytes IS '数据总字节数';
COMMENT ON COLUMN asset_dataset.size_bucket_code IS '大小区间编码（字典 size_bucket 的有效 itemCode）';

-- ---- asset_alias 表（重命名永久别名） ----

CREATE TABLE IF NOT EXISTS asset_alias (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id        VARCHAR(40)  NOT NULL,
    old_namespace   VARCHAR(128) NOT NULL,
    old_name        VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_asset_alias_coordinate UNIQUE (old_namespace, old_name)
);

CREATE INDEX IF NOT EXISTS ix_asset_alias_asset ON asset_alias (asset_id);

COMMENT ON TABLE asset_alias IS '资产重命名永久别名表，保障旧 URL/引用的持续可解析';
COMMENT ON COLUMN asset_alias.asset_id IS '资产 ID';
COMMENT ON COLUMN asset_alias.old_namespace IS '旧命名空间';
COMMENT ON COLUMN asset_alias.old_name IS '旧名称';

-- ---- 全文索引 ----

-- name/display_name 三字母组 GIN 索引（加速 ILIKE 关键词搜索）。
CREATE INDEX IF NOT EXISTS ix_asset_name_trgm ON asset USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_asset_display_name_trgm ON asset USING gin (display_name gin_trgm_ops);

-- description tsvector GIN 索引（加速全文搜索）。
CREATE INDEX IF NOT EXISTS ix_asset_description_fts ON asset USING gin (to_tsvector('simple', COALESCE(description, '')));

-- ---- 补齐索引 ----

-- 注：ix_asset_org_project_status 已由 V12 创建，此处不再重复。
CREATE INDEX IF NOT EXISTS ix_asset_provisioning ON asset (provisioning_status) WHERE provisioning_status != 'COMPLETED';
