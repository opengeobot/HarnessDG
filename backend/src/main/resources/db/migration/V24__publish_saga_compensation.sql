/*
 * 功能: P0-B Saga 补偿——publish_request 新增 SAGA_COMPENSATION_FAILED 状态。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */

-- ===========================================================================
-- 1. 扩展 publish_request.status CHECK 约束，增加 SAGA_COMPENSATION_FAILED
-- ===========================================================================

ALTER TABLE publish_request
    DROP CONSTRAINT ck_publish_request_status;

ALTER TABLE publish_request
    ADD CONSTRAINT ck_publish_request_status
        CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'PUBLISHING',
                           'PUBLISHED', 'FAILED', 'SAGA_COMPENSATION_FAILED'));

COMMENT ON COLUMN publish_request.status IS
    '状态：SUBMITTED/APPROVED/REJECTED/PUBLISHING/PUBLISHED/FAILED/SAGA_COMPENSATION_FAILED';

-- ===========================================================================
-- 2. 补充状态说明
-- ===========================================================================
-- SAGA_COMPENSATION_FAILED:
--   正向流程 (P6 PG 状态推进 / P7 请求状态更新) 失败，且单次补偿重试也失败。
--   不删除已创建的 Gitea Tag（保持 "Tag 成功但 PG 失败不删除 Tag" 策略），
--   等待人工对账核验与手动修复。
-- ===========================================================================