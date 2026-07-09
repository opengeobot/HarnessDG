/*
 * 功能: 平台自定义指标 Bean，为审计/通知等关键业务路径提供 Micrometer 计数器。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.platform.observability.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final String CRITICAL_EVENTS_COUNTER = "aihub_webhook_critical_events_total";
    private static final String RECONCILIATION_COUNTER = "aihub_reconciliation_discrepancies_total";
    private static final String UPLOAD_EXPIRED_COUNTER = "aihub_upload_sessions_expired_total";
    private static final String MCP_TOOL_CALLS_COUNTER = "aihub_mcp_tool_calls_total";
    private static final String UPLOAD_SESSIONS_ACTIVE_GAUGE = "aihub_upload_sessions_active";
    private static final String ASSET_COUNT_BY_STATUS_GAUGE = "aihub_asset_count_by_status";
    private static final String JOB_PROCESSING_DURATION_TIMER = "aihub_job_processing_duration_seconds";

    private final MeterRegistry meterRegistry;
    private final AtomicLong jobQueueDepth = new AtomicLong();
    private final AtomicLong jobDeadCount = new AtomicLong();
    private final AtomicLong webhookInboxPending = new AtomicLong();
    private final AtomicLong uploadSessionsActive = new AtomicLong();
    private final Map<String, AtomicLong> assetCountByStatus = new ConcurrentHashMap<>();

    public PlatformMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        registerGauges();
    }

    private void registerGauges() {
        if (meterRegistry == null) return;
        Gauge.builder("aihub_job_queue_depth", jobQueueDepth, AtomicLong::doubleValue)
                .description("Current job queue depth")
                .register(meterRegistry);
        Gauge.builder("aihub_job_dead_count", jobDeadCount, AtomicLong::doubleValue)
                .description("Number of dead jobs")
                .register(meterRegistry);
        Gauge.builder("aihub_webhook_inbox_pending", webhookInboxPending, AtomicLong::doubleValue)
                .description("Pending webhook inbox events")
                .register(meterRegistry);
        Gauge.builder(UPLOAD_SESSIONS_ACTIVE_GAUGE, uploadSessionsActive, AtomicLong::doubleValue)
                .description("Active upload sessions")
                .register(meterRegistry);
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
        if (meterRegistry == null) return;
        Counter.builder(WEBHOOK_DELIVERIES_COUNTER)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录一次关键 Webhook 事件（Tag 删除、Force Push）。
     */
    public void recordCriticalEvent(String deliveryId, String eventType) {
        if (meterRegistry == null) return;
        Counter.builder(CRITICAL_EVENTS_COUNTER)
                .tag("deliveryId", deliveryId != null ? deliveryId : "unknown")
                .tag("type", eventType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录对账差异。
     */
    public void recordReconciliationDiscrepancy(String reconciler, String category) {
        if (meterRegistry == null) return;
        Counter.builder(RECONCILIATION_COUNTER)
                .tag("reconciler", reconciler)
                .tag("category", category)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录上传会话过期。
     */
    public void recordUploadSessionExpired() {
        if (meterRegistry == null) return;
        Counter.builder(UPLOAD_EXPIRED_COUNTER)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 更新 Job 队列深度指标。
     */
    public void updateJobQueueDepth(long depth) {
        jobQueueDepth.set(depth);
    }

    /**
     * 更新 Dead Job 计数。
     */
    public void updateJobDeadCount(long count) {
        jobDeadCount.set(count);
    }

    /**
     * 更新 Webhook Inbox 待处理计数。
     */
    public void updateWebhookInboxPending(long count) {
        webhookInboxPending.set(count);
    }

    /**
     * 记录一次 MCP 工具调用。
     *
     * @param tool   工具名称
     * @param status 调用结果（success / error）
     */
    public void recordToolCall(String tool, String status) {
        if (meterRegistry == null) return;
        Counter.builder(MCP_TOOL_CALLS_COUNTER)
                .tag("tool", tool != null ? tool : "unknown")
                .tag("status", status != null ? status : "unknown")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 更新活跃上传会话数。
     */
    public void updateUploadSessionsActive(long count) {
        uploadSessionsActive.set(count);
    }

    /**
     * 更新指定状态的资产计数。
     *
     * @param status 资产状态（如 DRAFT / PUBLISHED）
     * @param count  当前数量
     */
    public void updateAssetCountByStatus(String status, long count) {
        AtomicLong gauge = assetCountByStatus.computeIfAbsent(status, s -> {
            AtomicLong val = new AtomicLong();
            if (meterRegistry != null) {
                Gauge.builder(ASSET_COUNT_BY_STATUS_GAUGE, val, AtomicLong::doubleValue)
                        .tag("status", s)
                        .description("Asset count by status")
                        .register(meterRegistry);
            }
            return val;
        });
        gauge.set(count);
    }

    /**
     * 记录 Job 处理耗时。
     *
     * @param jobType  Job 类型
     * @param duration 处理耗时
     */
    public void recordJobProcessingDuration(String jobType, Duration duration) {
        if (meterRegistry == null) return;
        Timer.builder(JOB_PROCESSING_DURATION_TIMER)
                .tag("type", jobType != null ? jobType : "unknown")
                .description("Job processing duration in seconds")
                .register(meterRegistry)
                .record(duration);
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
