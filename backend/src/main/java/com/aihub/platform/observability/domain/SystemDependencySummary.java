/*
 * 功能: 系统依赖与健康摘要，聚合各依赖状态并给出总体就绪状态。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability.domain;

import java.util.List;

/**
 * 系统依赖与健康摘要。
 *
 * <p>对应 OpenAPI 契约 {@code SystemDependencySummary}：{@code status} 为总体就绪状态，
 * {@code dependencies} 为各依赖组件的精简健康列表。
 *
 * @param status       总体就绪状态
 * @param dependencies 各依赖组件状态（不可变）
 */
public record SystemDependencySummary(DependencyHealth status, List<DependencyStatus> dependencies) {

    public SystemDependencySummary {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    /**
     * 由依赖状态列表聚合摘要：总体状态取所有依赖中最差者；空列表视为 {@code UP}。
     *
     * @param dependencies 依赖状态列表
     * @return 系统依赖摘要
     */
    public static SystemDependencySummary aggregate(List<DependencyStatus> dependencies) {
        DependencyHealth overall = DependencyHealth.UP;
        for (DependencyStatus dependency : dependencies) {
            overall = DependencyHealth.worst(overall, dependency.status());
        }
        return new SystemDependencySummary(overall, dependencies);
    }
}
