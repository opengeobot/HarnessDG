-- V9__audit_extension.sql（全局版本号：V1 归属 identity，V2-V6/V8 归属 catalog，V7 归属 artifact）
-- 审计字段补全（07 SEC-05）：字段清单 actor、organization、action、resource、result、
-- sourceIp、userAgent、traceId、idempotencyKey、createdAt；organization 用于
-- 组织维度审计追溯，idempotency_key 关联幂等控制面请求。
-- public_id：契约 AuditLog.id 为 uuid（04 OpenAPI），存量行用 gen_random_uuid() 回填（PG13+ 内置）。
-- 注：created_at 索引 V1 已建（ix_audit_logs_created），此处不重复建。

ALTER TABLE audit_logs ADD COLUMN organization VARCHAR(128);
ALTER TABLE audit_logs ADD COLUMN idempotency_key VARCHAR(128);
ALTER TABLE audit_logs ADD COLUMN public_id UUID UNIQUE NOT NULL DEFAULT gen_random_uuid();
CREATE INDEX ix_audit_logs_actor ON audit_logs(actor);
