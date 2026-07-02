/*
 * 功能: Outbox 事件领域实体，承载与核心事务原子写入的事件负载。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.domain;

import java.time.Instant;

/**
 * Outbox 事件领域实体。
 *
 * <p>在产生事件的同一数据库事务内写入，再由后台可靠任务轮询发布，保证事件不丢。
 * {@code payload} 以原始 JSON 字符串承载（已脱敏）。{@code processedAt} 为 {@code null} 表示待投递。
 *
 * @param id            内部自增主键（创建时为 null）
 * @param eventId       事件全局唯一 ID（evt_）
 * @param aggregateType 聚合根类型
 * @param aggregateId   聚合根稳定业务 ID
 * @param eventType     事件类型
 * @param payload       事件负载（JSON 字符串，已脱敏）
 * @param headers       事件头（JSON 字符串，如 webhook 目标等路由信息）
 * @param occurredAt    事件业务发生时间
 * @param processedAt   事件处理完成时间，null 表示待投递
 * @param traceId       分布式追踪 ID
 * @param principalId   触发事件的主体 ID（可空）
 */
public record OutboxEvent(Long id,
                          String eventId,
                          String aggregateType,
                          String aggregateId,
                          String eventType,
                          String payload,
                          String headers,
                          Instant occurredAt,
                          Instant processedAt,
                          String traceId,
                          String principalId) {
}
