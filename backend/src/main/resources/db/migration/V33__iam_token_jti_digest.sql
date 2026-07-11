-- ============================================================================
-- 功能: 刷新令牌 jti 摘要存储——仅存 SHA-256 摘要，降低明文 jti 暴露面。
-- 依据: PRD §5.540
-- 时间: 2026-07-11
-- 作者: AxeXie
-- ============================================================================

ALTER TABLE iam_token ADD COLUMN IF NOT EXISTS jti_digest VARCHAR(64);

COMMENT ON COLUMN iam_token.jti_digest IS 'JWT jti 的 SHA-256 十六进制摘要，用于吊销/轮换查询';

-- 回填既有记录的 jti 摘要。
UPDATE iam_token
SET jti_digest = encode(sha256(convert_to(jti, 'UTF8')), 'hex')
WHERE jti_digest IS NULL AND jti IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_iam_token_jti_digest ON iam_token (jti_digest)
    WHERE jti_digest IS NOT NULL;
