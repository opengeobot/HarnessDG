/*
 * 功能: 幂等键唯一约束对齐到含 principal_id 的作用域——修复跨主体同键记录被静默丢弃。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */

-- ===========================================================================
-- 幂等键作用域唯一约束
--
-- REQ-IDM-001 规定作用域为 key + principalId + method + path，读路径
-- (JdbcIdempotencyStore.find) 已按此实现；但 V9 建的唯一约束只含
-- idempotency_key（全局唯一），使第二个主体用同一 key 写入时被
-- ON CONFLICT (idempotency_key) DO NOTHING 静默丢弃，该主体永远没有幂等记录。
--
-- 本迁移把唯一性对齐到 REQ 声明的作用域。前向迁移，不改 V9。
-- ===========================================================================

-- ---- 1. 存量清点：principal_id 由 V20 引入，旧行可能为 NULL ----
-- 唯一索引把 NULL 视为互不相等，含 NULL 的行天然不参与作用域唯一性判断。
-- 为使这些行与新约束语义一致且可被按主体查询命中，统一回填哨兵值。
UPDATE api_idempotency
SET principal_id = '<legacy-unattributed>'
WHERE principal_id IS NULL;

-- ---- 2. 删除仅含 idempotency_key 的全局唯一约束 ----
ALTER TABLE api_idempotency
    DROP CONSTRAINT IF EXISTS api_idempotency_idempotency_key_key;

-- ---- 3. 建立覆盖完整作用域的唯一约束 ----
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'api_idempotency'::regclass
          AND conname = 'uq_api_idempotency_scope'
    ) THEN
        ALTER TABLE api_idempotency
            ADD CONSTRAINT uq_api_idempotency_scope
            UNIQUE (idempotency_key, principal_id, method, path);
    END IF;
END $$;

COMMENT ON CONSTRAINT uq_api_idempotency_scope ON api_idempotency IS
    '幂等作用域唯一约束：key + principalId + method + path，与 REQ-IDM-001 声明的作用域一致';
