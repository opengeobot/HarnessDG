/*
 * 功能: 通知视图 DTO，与 OpenAPI NotificationView 字段严格对齐。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.application;

import com.aihub.notification.domain.Notification;
import java.time.Instant;
import java.util.Map;

/**
 * 通知视图。
 *
 * <p>字段名与 OpenAPI {@code NotificationView} 严格对齐：notificationId、eventType、i18nKey、
 * parameters、status、createdAt、readAt。
 *
 * @param notificationId 通知业务 ID
 * @param eventType      事件类型
 * @param i18nKey        国际化文案键
 * @param parameters     文案参数
 * @param status         状态：UNREAD / READ
 * @param createdAt      创建时间
 * @param readAt         标记已读时间（可空）
 */
public record NotificationView(String notificationId,
                                String eventType,
                                String i18nKey,
                                Map<String, Object> parameters,
                                String status,
                                Instant createdAt,
                                Instant readAt) {

    /**
     * 从领域实体构造视图。
     */
    public static NotificationView from(Notification n,
                                        java.util.function.Function<String, Map<String, Object>> paramParser) {
        return new NotificationView(
                n.notificationId(),
                n.eventType(),
                n.i18nKey(),
                paramParser.apply(n.parameters()),
                n.read() == 1 ? "READ" : "UNREAD",
                n.createdAt(),
                n.readAt());
    }
}
