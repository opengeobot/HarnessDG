/*
 * 功能: 任务指标出站端口，由任务模块的基础设施适配器实现，供可观测性摘要服务聚合。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.platform.observability.domain;

/**
 * 任务指标出站端口。
 *
 * <p>平台可观测性层定义此端口以解耦对任务模块的直接依赖；任务模块的基础设施适配器
 * 实现该端口，将任务状态计数提供给指标摘要服务。端口缺失时（如仅 REST 角色实例）以 0 兜底。
 */
public interface JobMetricsPort {

    /**
     * 统计指定状态的任务数。
     *
     * @param status 任务状态（如 PENDING / RUNNING / DEAD）
     * @return 该状态任务数
     */
    long countByStatus(String status);
}
