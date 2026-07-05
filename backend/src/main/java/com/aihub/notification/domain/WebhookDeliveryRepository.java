/*
 * 功能: Webhook 投递仓储端口，定义创建、领取与状态更新能力。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.domain;

import java.time.Instant;
import java.util.List;

/**
 * Webhook 投递仓储端口。
 */
public interface WebhookDeliveryRepository {

    /** 创建投递记录。 */
    void insert(WebhookDelivery delivery);

    /**
     * 领取待重试的失败投递（status IN (PENDING, FAILED) 且 next_retry_at <= now）。
     *
     * @param now   当前时间
     * @param limit 最多领取条数
     * @return 领取到的投递记录列表
     */
    List<WebhookDelivery> claimRetryable(Instant now, int limit);

    /** 标记投递成功。 */
    void markDelivered(String deliveryId, int httpStatus, Instant now);

    /** 标记投递失败（可重试），更新 attempts 与 next_retry_at。 */
    void markFailed(String deliveryId, int attempts, Integer httpStatus, String error,
                    Instant nextRetryAt, Instant now);

    /** 标记投递放弃（DEAD）。 */
    void markDead(String deliveryId, int attempts, Integer httpStatus, String error, Instant now);

    /** 查询最近投递记录（管理员视图）。 */
    List<WebhookDelivery> listRecent(int limit);

    /** 按状态统计投递数。 */
    long countByStatus(WebhookDeliveryStatus status);

    /** 重置失败投递为 PENDING（管理员手动重试）。 */
    boolean resetForRetry(String deliveryId, Instant now);
}
