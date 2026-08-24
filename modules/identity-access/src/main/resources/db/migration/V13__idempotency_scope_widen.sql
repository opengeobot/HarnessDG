-- V13__idempotency_scope_widen.sql
-- 功能：幂等 scope 字段放宽（由 VARCHAR(64) 扩至 VARCHAR(255)，容纳 UUID 参数化路径）
-- 作者：AxeXie
-- 日期：2026-08-24
-- 幂等域对齐（04 §10）：scope = 主体 + HTTP 方法 + 归一化路径，
-- 参数化路径（含 UUID）超出原 VARCHAR(64)，放宽以容纳完整 scope。
ALTER TABLE idempotency_records ALTER COLUMN scope TYPE VARCHAR(255);
