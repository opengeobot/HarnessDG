-- 发布请求增加 CHANGES_REQUESTED 状态（REQUEST_CHANGES 决策后）
ALTER TABLE publish_request DROP CONSTRAINT IF EXISTS ck_publish_request_status;
ALTER TABLE publish_request ADD CONSTRAINT ck_publish_request_status
    CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CHANGES_REQUESTED', 'PUBLISHING', 'PUBLISHED', 'FAILED'));
