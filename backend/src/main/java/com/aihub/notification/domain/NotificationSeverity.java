/*
 * 功能: 通知严重等级枚举，与 notification.severity 约束对齐。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.domain;

/**
 * 通知严重等级。
 *
 * <p>取值与数据库 {@code ck_notification_severity} 约束严格对齐。
 */
public enum NotificationSeverity {

    /** 信息。 */
    INFO,

    /** 警告。 */
    WARN,

    /** 错误。 */
    ERROR,

    /** 严重。 */
    CRITICAL
}
