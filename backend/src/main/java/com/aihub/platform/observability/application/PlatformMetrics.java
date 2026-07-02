/*
 * 功能: 平台自定义指标 Bean，为审计/通知等关键业务路径提供 Micrometer 计数器。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.platform.observability.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 平台自定义指标。
 *
 * <p>为审计事件、Webhook 投递等关键业务路径注册 {@code aihub_} 前缀的 Counter，
 * 供 Prometheus 端点暴露。无 MeterRegistry 时（如纯单元测试）以空操作兜底，绝不抛出。
 *
 * <p>不引入额外 OTel agent 依赖；异步任务用 MDC traceId 传播（ContextPropagatingTaskDecorator 已实现）。
 */
@Service
public class PlatformMetrics {

    private static final String AUDIT_EVENTS_COUNTER = "aihub_audit_events_total";
    private static final String WEBHOOK_DELIVERIES_COUNTER = "aihub_webhook_deliveries_total";

    private final MeterRegistry meterRegistry;

    public PlatformMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    /**
     * 记录一次审计事件计数。
     *
     * @param eventType 审计事件类型（如 ROLE_CREATED）
     * @param result    审计结果（SUCCEEDED / FAILED / DENIED）
     */
    public void recordAuditEvent(String eventType, String result) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(AUDIT_EVENTS_COUNTER)
                .tag("type", eventType)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录一次 Webhook 投递结果计数。
     *
     * @param status 投递结果状态（DELIVERED / FAILED / DEAD）
     */
    public void recordWebhookDelivery(String status) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(WEBHOOK_DELIVERIES_COUNTER)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 读取审计事件计数器累计值（用于测试断言与摘要）。
     *
     * @param eventType 审计事件类型
     * @param result    审计结果
     * @return 累计计数，无注册表时返回 0
     */
    public long auditEventCount(String eventType, String result) {
        if (meterRegistry == null) {
            return 0;
        }
        AtomicLong holder = new AtomicLong();
        meterRegistry.find(AUDIT_EVENTS_COUNTER)
                .tag("type", eventType)
                .tag("result", result)
                .counters()
                .forEach(counter -> holder.addAndGet((long) counter.count()));
        return holder.get();
    }

    /**
     * 读取 Webhook 投递计数器累计值（用于测试断言与摘要）。
     *
     * @param status 投递结果状态
     * @return 累计计数，无注册表时返回 0
     */
    public long webhookDeliveryCount(String status) {
        if (meterRegistry == null) {
            return 0;
        }
        AtomicLong holder = new AtomicLong();
        meterRegistry.find(WEBHOOK_DELIVERIES_COUNTER)
                .tag("status", status)
                .counters()
                .forEach(counter -> holder.addAndGet((long) counter.count()));
        return holder.get();
    }
}
