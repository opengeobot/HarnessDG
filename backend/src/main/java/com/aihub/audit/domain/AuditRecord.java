/*
 * 功能: 审计记录领域实体，承载追加写审计日志的不可变字段。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.audit.domain;

import java.time.Instant;

/**
 * 审计记录领域实体。
 *
 * <p>持久化于 {@code audit_log} 表，追加写、不可篡改。{@code requestSummary} 以已脱敏的原始 JSON 字符串承载，
 * 避免领域层依赖序列化框架。{@code id} 为内部自增主键，仅查询结果回填，追加时为 {@code null}。
 *
 * @param id             内部自增主键（追加时为 null）
 * @param auditId        审计记录业务 ID（aud_）
 * @param eventType      审计事件类型
 * @param principalId    操作者主体 ID
 * @param principalType  操作者主体类型
 * @param action         动作
 * @param resourceType   目标资源类型
 * @param resourceId     目标资源业务 ID
 * @param scopeType      作用域类型
 * @param scopeId        作用域 ID
 * @param result         结果
 * @param errorCode      失败/拒绝错误码
 * @param requestSummary 已脱敏请求摘要（JSON 字符串）
 * @param traceId        追踪 ID
 * @param requestId      请求 ID
 * @param durationMs     耗时（毫秒）
 * @param occurredAt     发生时间
 */
public record AuditRecord(Long id,
                          String auditId,
                          String eventType,
                          String principalId,
                          String principalType,
                          String action,
                          String resourceType,
                          String resourceId,
                          String scopeType,
                          String scopeId,
                          AuditResult result,
                          String errorCode,
                          String requestSummary,
                          String traceId,
                          String requestId,
                          Long durationMs,
                          Instant occurredAt) {
}
