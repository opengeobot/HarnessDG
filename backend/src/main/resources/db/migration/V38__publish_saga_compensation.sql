/*
 * 功能: P0-B Saga 补偿——publish_request 新增 SAGA_COMPENSATION_FAILED 状态。
 * 时间: 2026-07-30（2026-09-22 由 V24 前向重排为 V38，并集合并远端 V26 的 CHANGES_REQUESTED）
 * 作者: AxeXie
 */

-- ===========================================================================
-- 1. 扩展 publish_request.status CHECK 约束，增加 SAGA_COMPENSATION_FAILED
--    注意：远端 V26__publish_request_changes_requested.sql 已把该约束重建为含
--    CHANGES_REQUESTED 的集合。本迁移在其之后执行，必须取两侧状态集的并集，
--    否则会丢失 CHANGES_REQUESTED。
-- ===========================================================================

ALTER TABLE publish_request
    DROP CONSTRAINT IF EXISTS ck_publish_request_status;

ALTER TABLE publish_request
    ADD CONSTRAINT ck_publish_request_status
        CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CHANGES_REQUESTED',
                           'PUBLISHING', 'PUBLISHED', 'FAILED', 'SAGA_COMPENSATION_FAILED'));

COMMENT ON COLUMN publish_request.status IS
    '状态：SUBMITTED/APPROVED/REJECTED/CHANGES_REQUESTED/PUBLISHING/PUBLISHED/FAILED/SAGA_COMPENSATION_FAILED';

-- ===========================================================================
-- 2. 补充状态说明
-- ===========================================================================
-- SAGA_COMPENSATION_FAILED:
--   正向流程 (P6 PG 状态推进 / P7 请求状态更新) 失败，且单次补偿重试也失败。
--   不删除已创建的 Gitea Tag（保持 "Tag 成功但 PG 失败不删除 Tag" 策略），
--   等待人工对账核验与手动修复。
-- ===========================================================================