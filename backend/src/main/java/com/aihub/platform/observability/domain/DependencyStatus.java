/*
 * 功能: 单个依赖组件的健康状态，承载组件名、状态与探测延迟。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability.domain;

/**
 * 单个依赖组件的健康状态。
 *
 * <p>仅暴露运维诊断所需的非敏感信息（名称、状态、探测延迟），不包含内部 Endpoint、
 * 凭据或网络拓扑，符合设计第 5.14 节对系统诊断信息的脱敏要求。
 *
 * @param name      依赖名称（如 {@code postgres}）
 * @param status    依赖健康状态
 * @param latencyMs 探测延迟（毫秒），探测失败时可为 {@code null}
 */
public record DependencyStatus(String name, DependencyHealth status, Long latencyMs) {

    public DependencyStatus {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("dependency name must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("dependency status must not be null");
        }
    }
}
