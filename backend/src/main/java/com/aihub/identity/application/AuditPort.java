/*
 * 功能: identity 模块审计端口——认证/令牌/口令等事件的审计写出抽象，含结果与错误码。
 * 时间: 2026-07-02
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.audit.domain.AuditResult;
import java.util.Map;

/**
 * identity 模块审计端口。
 *
 * <p>由 infrastructure 层桥接到权威 AuditService。与其它模块的简化 AuditPort 不同，
 * 认证域需记录成功/失败/拒绝三态，故签名额外携带 {@link AuditResult} 与错误码。
 *
 * <p>调用方严禁将 JWT/refresh/口令/凭据明文写入 {@code attributes}。
 */
public interface AuditPort {

    /**
     * 追加一条审计事件（旁路，失败不得影响认证主流程）。
     *
     * @param eventType  事件类型（对齐契约 x-audit-event 语义）
     * @param result     结果：SUCCEEDED/FAILED/DENIED
     * @param actorId    操作者主体 ID（可空，缺省由上下文补齐）
     * @param targetId   目标资源业务 ID（可空）
     * @param errorCode  失败/拒绝错误码（成功时传 null）
     * @param attributes 结构化属性（落库前脱敏；不得含凭据/令牌明文）
     */
    void record(String eventType,
                AuditResult result,
                String actorId,
                String targetId,
                String errorCode,
                Map<String, Object> attributes);
}
