/*
 * 功能: 审计记录视图 DTO，与 OpenAPI AuditLogView 字段对齐。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.audit.application;

import com.aihub.audit.domain.AuditRecord;
import java.time.Instant;

/**
 * 审计记录视图。
 *
 * <p>字段名与 OpenAPI {@code AuditLogView} 对齐：auditId、principalId、action、resourceType、
 * resourceId、result、errorCode、requestId、traceId、occurredAt。不返回持久化实体或内部主键。
 *
 * @param auditId      审计记录业务 ID
 * @param principalId  操作者主体 ID
 * @param action       动作
 * @param resourceType 资源类型
 * @param resourceId   资源业务 ID
 * @param result       结果（SUCCEEDED/FAILED/DENIED）
 * @param errorCode    错误码（可空）
 * @param requestId    请求 ID（可空）
 * @param traceId      追踪 ID（可空）
 * @param occurredAt   发生时间
 */
public record AuditLogView(String auditId,
                           String principalId,
                           String action,
                           String resourceType,
                           String resourceId,
                           String result,
                           String errorCode,
                           String requestId,
                           String traceId,
                           Instant occurredAt) {

    /**
     * 从领域实体构造视图。
     */
    public static AuditLogView from(AuditRecord record) {
        return new AuditLogView(record.auditId(), record.principalId(), record.action(),
                record.resourceType(), record.resourceId(),
                record.result() == null ? null : record.result().name(),
                record.errorCode(), record.requestId(), record.traceId(), record.occurredAt());
    }
}
