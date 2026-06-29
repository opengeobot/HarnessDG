/*
 * 功能: 依赖健康状态枚举，作为系统诊断摘要的稳定语义值。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability.domain;

/**
 * 依赖健康状态。
 *
 * <p>语义稳定的三态：{@code UP} 正常、{@code DEGRADED} 降级、{@code DOWN} 不可用，
 * 与 OpenAPI 契约 {@code DependencyStatus.status} 保持一致。{@link #worst} 用于聚合总体状态。
 */
public enum DependencyHealth {

    /** 正常。 */
    UP,

    /** 降级（部分能力受影响但可用）。 */
    DEGRADED,

    /** 不可用。 */
    DOWN;

    /**
     * 取两个状态中更差的一个，用于聚合总体就绪状态。
     *
     * @param first  状态一
     * @param second 状态二
     * @return 严重程度更高的状态（DOWN &gt; DEGRADED &gt; UP）
     */
    public static DependencyHealth worst(DependencyHealth first, DependencyHealth second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }
}
