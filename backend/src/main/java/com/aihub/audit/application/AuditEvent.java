/*
 * 功能: 审计事件输入对象，承载一次待记录审计的全部字段（已脱敏前的结构化属性）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.audit.application;

import com.aihub.audit.domain.AuditResult;
import java.util.Map;

/**
 * 审计事件输入对象。
 *
 * <p>由各业务模块经其 AuditPort 经桥接适配后构造，交给 {@link AuditService} 追加写。
 * {@code attributes} 在落库前由 {@link AuditService} 做字段级脱敏。
 *
 * @param eventType    审计事件类型（如 ROLE_CREATED）
 * @param action       动作（默认同 eventType）
 * @param principalId  操作者主体 ID
 * @param principalType 操作者主体类型（可空）
 * @param resourceType 资源类型（可空，可由 eventType 推导）
 * @param resourceId   资源业务 ID
 * @param scopeType    作用域类型（可空）
 * @param scopeId      作用域 ID（可空）
 * @param result       结果
 * @param errorCode    失败/拒绝错误码（可空）
 * @param attributes   结构化属性（落库前脱敏）
 */
public record AuditEvent(String eventType,
                         String action,
                         String principalId,
                         String principalType,
                         String resourceType,
                         String resourceId,
                         String scopeType,
                         String scopeId,
                         AuditResult result,
                         String errorCode,
                         Map<String, Object> attributes) {
}
