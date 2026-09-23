-- ----------------------------------------------------------------------------
-- 功能: upload_session 状态约束补齐 PROCESSING/FAILED 两个合法生命周期状态。
-- 时间: 2026-09-23
-- 作者: AxeXie
-- 说明: 前向迁移。V16 建立 ck_upload_session_status 时遗漏了领域枚举
--       UploadSessionStatus 的 PROCESSING（Multipart 已完成、物化 Job 进行中）与
--       FAILED（物化失败）两个状态，导致 completeSession 把会话推进到 PROCESSING 时
--       违反 CHECK 约束（数据库层 fail-closed，正确暴露了契约漂移）。
--       领域生命周期：OPEN → COMMITTING → PROCESSING → COMPLETED/FAILED，或 CANCELLED/EXPIRED。
--       不修改 V16__version_transfer.sql 等已应用迁移。
-- ----------------------------------------------------------------------------

ALTER TABLE upload_session
    DROP CONSTRAINT IF EXISTS ck_upload_session_status;

ALTER TABLE upload_session
    ADD CONSTRAINT ck_upload_session_status
    CHECK (status IN ('OPEN', 'COMMITTING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED'));

COMMENT ON CONSTRAINT ck_upload_session_status ON upload_session
    IS '会话状态枚举，与 UploadSessionStatus 领域枚举保持一致（含 PROCESSING/FAILED）';

COMMENT ON COLUMN upload_session.status
    IS '会话状态：OPEN/COMMITTING/PROCESSING/COMPLETED/FAILED/CANCELLED/EXPIRED';
