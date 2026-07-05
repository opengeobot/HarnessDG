/*
 * 功能: 系统告警领域实体。
 * 时间: 2026-07-06
 * 作者: AxeXie
 */
package com.aihub.platform.observability.domain;

import java.time.Instant;

/**
 * 系统告警领域实体。
 *
 * @param alertId        告警唯一 ID
 * @param alertType      告警类型
 * @param severity       严重等级
 * @param title          告警标题
 * @param detail         告警详情
 * @param status         状态（FIRING / RESOLVED）
 * @param sourceMetric   触发指标名
 * @param thresholdValue 阈值
 * @param currentValue   当前值
 * @param firedAt        触发时间
 * @param resolvedAt     恢复时间
 * @param traceId        追踪 ID
 */
public record SystemAlert(Long id,
                           String alertId,
                           String alertType,
                           String severity,
                           String title,
                           String detail,
                           String status,
                           String sourceMetric,
                           Double thresholdValue,
                           Double currentValue,
                           Instant firedAt,
                           Instant resolvedAt,
                           String traceId) {
}
