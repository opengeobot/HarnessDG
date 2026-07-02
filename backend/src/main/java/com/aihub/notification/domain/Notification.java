/*
 * 功能: 通知领域实体，承载站内通知的不可变字段。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.domain;

import java.time.Instant;

/**
 * 通知领域实体。
 *
 * <p>持久化于 {@code notification} 表。{@code parameters} 以原始 JSON 字符串承载（已脱敏），
 * 避免领域层依赖序列化框架。{@code id} 为内部自增主键，仅查询结果回填，创建时为 {@code null}。
 *
 * @param id             内部自增主键（创建时为 null）
 * @param notificationId 通知业务 ID（ntf_）
 * @param principalId    目标主体 ID
 * @param eventType      事件类型（如 JOB_DEAD）
 * @param i18nKey        国际化文案键
 * @param parameters     文案参数（JSON 字符串，已脱敏）
 * @param severity       严重等级
 * @param read           是否已读：0 未读 / 1 已读
 * @param createdAt      创建时间
 * @param readAt         标记已读时间（可空）
 * @param rowVersion      乐观锁版本号
 */
public record Notification(Long id,
                           String notificationId,
                           String principalId,
                           String eventType,
                           String i18nKey,
                           String parameters,
                           NotificationSeverity severity,
                           int read,
                           Instant createdAt,
                           Instant readAt,
                           int rowVersion) {
}
