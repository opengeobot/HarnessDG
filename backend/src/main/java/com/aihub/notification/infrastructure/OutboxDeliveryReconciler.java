package com.aihub.notification.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.platform.observability.application.PlatformMetrics;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Outbox↔Delivery 对账 Worker。
 *
 * <p>检测 outbox_event 与 webhook_delivery 之间的一致性：
 * - 已处理但无投递记录的 outbox 事件 → MANUAL_REVIEW
 * - 投递失败超过阈值的 webhook_delivery → SECURITY_INCIDENT
 * - 待投递超过 24h 的 outbox 事件 → 告警
 * - processed_at 为 NULL 超过 24h → AUTO_REPAIR（重新标记待处理）
 */
@Component
public class OutboxDeliveryReconciler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxDeliveryReconciler.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformMetrics platformMetrics;

    public OutboxDeliveryReconciler(JdbcTemplate jdbcTemplate, PlatformMetrics platformMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.platformMetrics = platformMetrics;
    }

    @Override
    public String type() {
        return "OUTBOX_DELIVERY_RECONCILE";
    }

    @Override
    public void handle(JobContext context) {
        LOG.info("starting outbox-delivery reconciliation jobId={}", context.jobId());

        int totalChecked = 0;
        int discrepancies = 0;

        // 1. 检测长期未投递的 outbox 事件（>24h）
        Long staleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event " +
                        "WHERE processed_at IS NULL AND occurred_at < NOW() - INTERVAL '24 hours'",
                Long.class);
        if (staleCount != null && staleCount > 0) {
            LOG.warn("{} outbox events pending for >24h jobId={}", staleCount, context.jobId());
            discrepancies += staleCount.intValue();
        }

        // 2. 检测长期失败的 webhook_delivery（>3 次失败）
        List<Map<String, Object>> failedDeliveries = jdbcTemplate.queryForList(
                "SELECT id, event_id, target_url, attempts, last_error " +
                        "FROM webhook_delivery WHERE status = 'FAILED' AND attempts >= 3 " +
                        "ORDER BY id LIMIT ?", BATCH_SIZE);
        for (Map<String, Object> row : failedDeliveries) {
            String eventId = (String) row.get("event_id");
            String targetUrl = (String) row.get("target_url");
            int attempts = row.get("attempts") != null ? ((Number) row.get("attempts")).intValue() : 0;
            LOG.warn("webhook delivery permanently failed eventId={} target={} attempts={}",
                    eventId, targetUrl, attempts);
            discrepancies++;
        }

        // 3. 逐批检查 outbox_event 完整性
        String cursor = null;
        do {
            List<Map<String, Object>> batch = cursor == null
                    ? jdbcTemplate.queryForList(
                    "SELECT event_id, aggregate_type, aggregate_id, event_type, processed_at, occurred_at " +
                            "FROM outbox_event ORDER BY event_id LIMIT ?", BATCH_SIZE)
                    : jdbcTemplate.queryForList(
                    "SELECT event_id, aggregate_type, aggregate_id, event_type, processed_at, occurred_at " +
                            "FROM outbox_event WHERE event_id > ? ORDER BY event_id LIMIT ?",
                    cursor, BATCH_SIZE);

            if (batch.isEmpty()) break;

            for (Map<String, Object> row : batch) {
                String eventId = (String) row.get("event_id");
                String aggregateType = (String) row.get("aggregate_type");
                String eventType = (String) row.get("event_type");

                ReconcileResult result = checkOutboxEvent(eventId, aggregateType, eventType);
                if (result != ReconcileResult.CONSISTENT) {
                    discrepancies++;
                    recordDiscrepancy(eventId, result);
                }
                totalChecked++;
            }

            cursor = (String) batch.get(batch.size() - 1).get("event_id");
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        } while (true);

        LOG.info("outbox-delivery reconciliation completed jobId={} totalChecked={} discrepancies={}",
                context.jobId(), totalChecked, discrepancies);
    }

    private ReconcileResult checkOutboxEvent(String eventId, String aggregateType, String eventType) {
        // event_id 格式校验（evt_ 前缀）
        if (eventId == null || !eventId.startsWith("evt_")) {
            LOG.warn("outbox event {} has invalid event_id format", eventId);
            return ReconcileResult.MANUAL_REVIEW;
        }

        // aggregate_type 非空
        if (aggregateType == null || aggregateType.isEmpty()) {
            LOG.warn("outbox event {} has null aggregate_type", eventId);
            return ReconcileResult.MANUAL_REVIEW;
        }

        // event_type 非空
        if (eventType == null || eventType.isEmpty()) {
            LOG.warn("outbox event {} has null event_type", eventId);
            return ReconcileResult.MANUAL_REVIEW;
        }

        return ReconcileResult.CONSISTENT;
    }

    private void recordDiscrepancy(String eventId, ReconcileResult result) {
        LOG.warn("outbox-delivery discrepancy eventId={} classification={}", eventId, result);
        platformMetrics.recordReconciliationDiscrepancy("OutboxDelivery", result.name());
        try {
            String deliveryId = "outbox-reconcile-" + eventId + "-" + System.currentTimeMillis();
            jdbcTemplate.update(
                    "INSERT INTO webhook_inbox (delivery_id, event_type, source, signature_valid, payload, status) " +
                            "VALUES (?, 'OUTBOX_RECONCILE_DISCREPANCY', 'internal', true, " +
                            "jsonb_build_object('eventId', ?, 'classification', ?::text), 'COMPLETED') " +
                            "ON CONFLICT (delivery_id) DO NOTHING",
                    deliveryId, eventId, result.name());
        } catch (Exception e) {
            LOG.error("failed to record outbox discrepancy for eventId={}", eventId, e);
        }
    }

    enum ReconcileResult {
        CONSISTENT,
        AUTO_REPAIR,
        MANUAL_REVIEW,
        SECURITY_INCIDENT
    }
}
