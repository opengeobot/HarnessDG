-- 功能: 扩展 iam_token 支持 PAT（个人访问令牌）类型与显示名称。
-- 时间: 2026-07-11
-- 作者: AxeXie

ALTER TABLE iam_token DROP CONSTRAINT IF EXISTS ck_iam_token_type;

ALTER TABLE iam_token ADD CONSTRAINT ck_iam_token_type
    CHECK (token_type IN ('ACCESS', 'REFRESH', 'PAT'));

ALTER TABLE iam_token ADD COLUMN IF NOT EXISTS name VARCHAR(128);

COMMENT ON COLUMN iam_token.name IS 'PAT 显示名称（仅 token_type=PAT 时使用，便于用户识别）';

CREATE INDEX IF NOT EXISTS ix_iam_token_pat_principal
    ON iam_token (principal_id, token_type)
    WHERE token_type = 'PAT';
