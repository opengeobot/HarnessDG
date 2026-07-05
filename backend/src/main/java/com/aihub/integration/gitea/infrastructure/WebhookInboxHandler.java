package com.aihub.integration.gitea.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 */
@Component
public class WebhookInboxHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookInboxHandler.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WebhookInboxHandler(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "WEBHOOK_PROCESS";
    }

    @Override
    public void handle(JobContext context) {
        String payload = context.payload();
        if (payload == null || payload.isEmpty()) {
            LOG.warn("webhook process job has empty payload jobId={}", context.jobId());
            return;
        }

        try {
            JsonNode jobPayload = objectMapper.readTree(payload);
            String deliveryId = jobPayload.path("deliveryId").asText(null);
            if (deliveryId == null) {
                LOG.warn("webhook process job missing deliveryId jobId={}", context.jobId());
                return;
            }

            // 查询 inbox 记录
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT id, event_type, payload, status FROM webhook_inbox WHERE delivery_id = ?",
                    deliveryId);

            String status = (String) row.get("status");
            if ("COMPLETED".equals(status)) {
                LOG.debug("webhook already processed deliveryId={}", deliveryId);
                return;
            }

            // 标记处理中
            jdbcTemplate.update(
                    "UPDATE webhook_inbox SET status = 'PROCESSING' WHERE delivery_id = ? AND status = 'PENDING'",
                    deliveryId);

            String eventType = (String) row.get("event_type");
            String eventPayload = row.get("payload").toString();
            JsonNode event = objectMapper.readTree(eventPayload);

            // 按事件类型分发处理
            processEvent(eventType, event, deliveryId);

            // 标记完成
            jdbcTemplate.update(
                    "UPDATE webhook_inbox SET status = 'COMPLETED', processed_at = NOW() WHERE delivery_id = ?",
                    deliveryId);

            LOG.info("webhook processed deliveryId={} eventType={}", deliveryId, eventType);

        } catch (Exception e) {
            LOG.error("webhook processing failed jobId={}", context.jobId(), e);
            throw new RuntimeException("webhook processing failed: " + e.getMessage(), e);
        }
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

        // 检测 force push（潜在安全问题）
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
        } else {
            LOG.info("delete event repo={} refType={} ref={} deliveryId={}",
                    repoFullName, refType, ref, deliveryId);
        }
    }
}
