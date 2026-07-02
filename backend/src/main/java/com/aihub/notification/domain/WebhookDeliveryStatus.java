/*
 * 功能: Webhook 投递状态枚举，与 webhook_delivery.status 约束对齐。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.domain;

/**
 * Webhook 投递状态。
 *
 * <p>取值与数据库 {@code ck_webhook_delivery_status} 约束严格对齐。
 */
public enum WebhookDeliveryStatus {

    /** 待投递。 */
    PENDING,

    /** 已投递成功。 */
    DELIVERED,

    /** 投递失败（可重试）。 */
    FAILED,

    /** 放弃（重试耗尽）。 */
    DEAD
}
