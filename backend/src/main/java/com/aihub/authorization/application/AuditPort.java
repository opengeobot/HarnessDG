/*
 * 功能: 审计接入端口，授权管理写操作的审计事件在此发出；
 *       由 infrastructure 层 AuthorizationAuditAdapter 实现，委托 AuditService 持久化。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.application;

import java.util.Map;

/**
 * 审计接入端口。
 *
 * <p>由 {@code AuthorizationAuditAdapter} 提供实现，委托 {@code AuditService} 写入审计日志。
 */
public interface AuditPort {

    /**
     * 记录一条审计事件接入点。
     *
     * @param eventType 审计事件类型（与契约 x-audit-event 对齐，如 ROLE_CREATED）
     * @param actorId   操作者主体 ID
     * @param targetId  目标资源业务 ID
     * @param attributes 已脱敏的结构化属性（不得含密钥/凭据/令牌）
     */
    void record(String eventType, String actorId, String targetId, Map<String, Object> attributes);
}
