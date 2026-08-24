package com.modelhub.shared.metrics;

/**
 * 业务安全计数器（07 §5.2）：登录失败、鉴权拒绝、限流触发。
 * shared 不依赖 micrometer，默认实现为空操作；
 * app 装配层提供基于 MeterRegistry 的实现（BusinessMetrics）。
 */
public interface BusinessCounters {

    default void loginFailure() {
    }

    default void authzDenial() {
    }

    default void rateLimitTrigger() {
    }
}
