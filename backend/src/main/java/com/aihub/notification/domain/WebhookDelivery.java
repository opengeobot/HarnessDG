/*
 * 功能: Webhook 投递记录领域实体，承载出站投递状态机。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.domain;

import java.time.Instant;

/**
 * Webhook 投递记录领域实体。
 *
 * <p>持久化于 {@code webhook_delivery} 表。{@code payload} 以原始 JSON 字符串承载（已脱敏）。
 * 签名头格式为 {@code t=<timestamp>,v1=<hmac>}。失败按指数退避重试，超阈值进入 DEAD。
 *
 * @param id               内部自增主键（创建时为 null）
 * @param deliveryId       投递唯一 ID（whk_）
 * @param eventId          关联事件 ID
 * @param targetUrl        投递目标 URL（已校验非内网/保留地址）
 * @param payload          投递负载（JSON 字符串，已脱敏）
 * @param signatureHeader  签名头（X-AIHub-Signature: t=,v1=）
 * @param status           投递状态
 * @param attempts         已尝试次数
 * @param lastResponseCode 最近一次 HTTP 响应码
 * @param lastError        最近一次失败错误信息（已脱敏）
 * @param nextRetryAt      下次重试时间（退避后）
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 */
public record WebhookDelivery(Long id,
                              String deliveryId,
                              String eventId,
                              String targetUrl,
                              String payload,
                              String signatureHeader,
                              WebhookDeliveryStatus status,
                              int attempts,
                              Integer lastResponseCode,
                              String lastError,
                              Instant nextRetryAt,
                              Instant createdAt,
                              Instant updatedAt) {
}
