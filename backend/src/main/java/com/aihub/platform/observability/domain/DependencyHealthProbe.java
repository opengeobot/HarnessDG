/*
 * 功能: 依赖健康探测端口，由基础设施适配器实现具体依赖的探测逻辑。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability.domain;

/**
 * 依赖健康探测端口（Port）。
 *
 * <p>领域只定义探测契约，具体依赖（PostgreSQL、Gitea、MinIO 等）的探测由各自的基础设施适配器实现并注册。
 * 应用服务聚合所有探测结果，新增依赖只需新增实现，无需修改聚合逻辑。
 */
public interface DependencyHealthProbe {

    /**
     * @return 依赖名称（用于诊断展示，应稳定）
     */
    String name();

    /**
     * 探测依赖当前健康状态。实现必须自行处理超时与异常，绝不向上抛出，
     * 探测失败应返回 {@link DependencyHealth#DOWN} 而非异常。
     *
     * @return 该依赖的健康状态
     */
    DependencyStatus probe();
}
