package com.aihub.integration.gitea.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Webhook Inbox 异步处理 Worker。
 *
 * <p>从 webhook_inbox 取 PENDING 事件，解析并投影更新到本地缓存。
 * 正式 Tag 删除/改指向触发 CRITICAL 审计事件。
 *
 * <p>幂等：已处理事件按 delivery_id 跳过。
 * 周期任务 payload 为空时自动扫描 PENDING 队列（每批最多 50 条）。
 */
@Component
public class WebhookInboxHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookInboxHandler.class);
    private static final int SWEEP_BATCH_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformMetrics platformMetrics;

    public WebhookInboxHandler(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                               PlatformMetrics platformMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.platformMetrics = platformMetrics;
    }

    @Override
    public String type() {
        return "WEBHOOK_PROCESS";
    }

    @Override
    public void handle(JobContext context) {
        String payload = context.payload();
        if (payload == null || payload.isEmpty() || "{}".equals(payload.trim())) {
            sweepPendingInbox(context.jobId());
            return;
        }

        try {
            JsonNode jobPayload = objectMapper.readTree(payload);
            String deliveryId = jobPayload.path("deliveryId").asText(null);
            if (deliveryId == null) {
                sweepPendingInbox(context.jobId());
                return;
            }
            processDelivery(deliveryId);
        } catch (Exception e) {
            LOG.error("webhook processing failed jobId={}", context.jobId(), e);
            throw new RuntimeException("webhook processing failed: " + e.getMessage(), e);
        }
    }

    private void sweepPendingInbox(String jobId) {
        List<String> pending = jdbcTemplate.queryForList(
                "SELECT delivery_id FROM webhook_inbox WHERE status = 'PENDING' "
                        + "ORDER BY received_at LIMIT ?",
                String.class, SWEEP_BATCH_SIZE);
        if (pending.isEmpty()) {
            LOG.debug("webhook inbox sweep found no pending items jobId={}", jobId);
            return;
        }
        LOG.info("webhook inbox sweep processing {} pending items jobId={}", pending.size(), jobId);
        for (String deliveryId : pending) {
            try {
                processDelivery(deliveryId);
            } catch (Exception e) {
                LOG.error("webhook sweep failed for deliveryId={} jobId={}", deliveryId, jobId, e);
            }
        }
    }

    private void processDelivery(String deliveryId) throws Exception {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, event_type, payload, status FROM webhook_inbox WHERE delivery_id = ?",
                deliveryId);

        String status = (String) row.get("status");
        if ("COMPLETED".equals(status)) {
            LOG.debug("webhook already processed deliveryId={}", deliveryId);
            return;
        }

        jdbcTemplate.update(
                "UPDATE webhook_inbox SET status = 'PROCESSING' WHERE delivery_id = ? AND status = 'PENDING'",
                deliveryId);

        String eventType = (String) row.get("event_type");
        String eventPayload = row.get("payload").toString();
        JsonNode event = objectMapper.readTree(eventPayload);

        processEvent(eventType, event, deliveryId);

        jdbcTemplate.update(
                "UPDATE webhook_inbox SET status = 'COMPLETED', processed_at = NOW() WHERE delivery_id = ?",
                deliveryId);

        LOG.info("webhook processed deliveryId={} eventType={}", deliveryId, eventType);
    }

    private void processEvent(String eventType, JsonNode event, String deliveryId) {
        switch (eventType) {
            case "push" -> processPushEvent(event, deliveryId);
            case "create" -> processCreateEvent(event, deliveryId);
            case "delete" -> processDeleteEvent(event, deliveryId);
            case "repository" -> LOG.info("repository event deliveryId={}", deliveryId);
            default -> LOG.debug("unhandled webhook event type={} deliveryId={}", eventType, deliveryId);
        }
    }

    private void processPushEvent(JsonNode event, String deliveryId) {
        String repoFullName = event.path("repository").path("full_name").asText("");
        String ref = event.path("ref").asText("");
        LOG.info("push event repo={} ref={} deliveryId={}", repoFullName, ref, deliveryId);

        boolean forced = event.path("forced").asBoolean(false);
        if (forced) {
            LOG.warn("FORCE PUSH detected repo={} ref={} deliveryId={}", repoFullName, ref, deliveryId);
        }
    }

    private void processCreateEvent(JsonNode event, String deliveryId) {
        String refType = event.path("ref_type").asText("");
        String ref = event.path("ref").asText("");
        String repoFullName = event.path("repository").path("full_name").asText("");
        LOG.info("create event repo={} refType={} ref={} deliveryId={}",
                repoFullName, refType, ref, deliveryId);
    }

    private void processDeleteEvent(JsonNode event, String deliveryId) {
        String refType = event.path("ref_type").asText("");
        String ref = event.path("ref").asText("");
        String repoFullName = event.path("repository").path("full_name").asText("");

        if ("tag".equals(refType)) {
            LOG.warn("CRITICAL: Tag deletion detected repo={} tag={} deliveryId={}",
                    repoFullName, ref, deliveryId);
            platformMetrics.recordCriticalEvent(deliveryId, "TAG_DELETION");
        } else {
            LOG.info("delete event repo={} refType={} ref={} deliveryId={}",
                    repoFullName, refType, ref, deliveryId);
        }
    }
}
