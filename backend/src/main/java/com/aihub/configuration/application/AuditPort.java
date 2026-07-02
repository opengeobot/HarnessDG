/*
 * 功能: configuration 审计接入端口（占位），配置写操作的审计事件在此发出；
 *       Task 10 审计模块落地后接入真实实现。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.application;

import java.util.Map;

/**
 * configuration 审计接入端口。
 *
 * <p><b>TODO(Task 10)：</b>审计模块尚未实现，本端口仅作为配置写操作的审计接入点占位。
 * 当前提供结构化日志实现（{@code LoggingConfigurationAuditPort}），<b>不写入持久化审计</b>；
 * Task 10 审计模块落地后由其提供权威实现替换。绝不记录密钥/凭据/令牌等敏感信息。
 */
public interface AuditPort {

    /**
     * 记录一条审计事件接入点。
     *
     * @param eventType  审计事件类型（与契约 x-audit-event 对齐）
     * @param actorId    操作者主体 ID
     * @param targetId   目标资源业务键（配置键）
     * @param attributes 已脱敏的结构化属性（不得含密钥/凭据/令牌）
     */
    void record(String eventType, String actorId, String targetId, Map<String, Object> attributes);
}
