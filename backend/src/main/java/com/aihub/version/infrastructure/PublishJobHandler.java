/*
 * 功能: 发布 Job Handler（不可变发布 Saga），含补偿/恢复机制。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 发布 Job Handler（不可变发布 Saga）。
 *
 * <p>处理 {@code VERSION_PUBLISH} 类型任务：
 * <ol>
 *   <li>P1 确认审批状态为 APPROVED</li>
 *   <li>P2 校验 Commit（冻结时摘要一致）</li>
 *   <li>P3 校验 DVC（工件哈希完整）</li>
 *   <li>P4 创建 Tag（Gitea，P3 简化为记录 git_tag）</li>
 *   <li>P5 验证保护（确认 Tag 唯一）</li>
 *   <li>P6 PG PUBLISHED 状态推进</li>
 *   <li>P7 Outbox 事件（P3 简化为审计日志）</li>
 * </ol>
 *
 * <p>Tag 冲突 → 标记请求 FAILED，永不覆盖。
 * <p>P6/P7 失败时执行单次补偿重试；补偿也失败 → SAGA_COMPENSATION_FAILED，人工介入。
 */
@Component
public class PublishJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PublishJobHandler.class);

    private final VersionRepository versionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PublishJobHandler(VersionRepository versionRepository,
                             JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper) {
        this.versionRepository = versionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "VERSION_PUBLISH";
    }

    @Override
    public void handle(JobContext context) throws Exception {
        JsonNode payload = objectMapper.readTree(context.payload());
        String requestId = payload.path("requestId").asText();
        String versionId = payload.path("versionId").asText();

        LOG.info("publishing version versionId={} requestId={}", versionId, requestId);

        // P1: 确认审批
        Map<String, Object> request = jdbcTemplate.queryForMap(
                "SELECT status, frozen_digest FROM publish_request WHERE request_id = ?", requestId);
        String reqStatus = String.valueOf(request.get("status"));
        if (!"APPROVED".equals(reqStatus) && !"PUBLISHING".equals(reqStatus)) {
            LOG.warn("publish request not in APPROVED/PUBLISHING status requestId={} status={}", requestId, reqStatus);
            return;
        }

        // 更新请求状态为 PUBLISHING（幂等）
        jdbcTemplate.update("UPDATE publish_request SET status = 'PUBLISHING' WHERE request_id = ? AND status IN ('APPROVED', 'PUBLISHING')",
                requestId);

        // P2: 校验版本状态
        Version version = versionRepository.findByVersionId(versionId).orElse(null);
        if (version == null) {
            markFailed(requestId, versionId, context.principalId());
            throw new IllegalStateException("version not found: " + versionId);
        }

        if (version.status() != VersionStatus.PENDING_REVIEW) {
            markFailed(requestId, versionId, context.principalId());
            throw new IllegalStateException("version not in PENDING_REVIEW: " + version.status());
        }

        // P3: 校验 Manifest 摘要一致性
        String frozenDigest = String.valueOf(request.get("frozen_digest"));
        if (version.manifestDigest() == null || !version.manifestDigest().equals(frozenDigest)) {
            markFailed(requestId, versionId, context.principalId());
            throw new IllegalStateException("manifest digest mismatch: expected=" + frozenDigest + " actual=" + version.manifestDigest());
        }

        // P4: Tag 冲突检测（防覆盖）
        String gitTag = version.version();
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_version WHERE git_tag = ? AND status = 'PUBLISHED'",
                Long.class, gitTag);
        if (existing != null && existing > 0) {
            markFailed(requestId, versionId, context.principalId());
            throw new IllegalStateException("git tag conflict: " + gitTag);
        }

        LOG.info("P4: tag {} reserved for versionId={}", gitTag, versionId);

        try {
            // P5-P6: 发布版本（PG 状态推进）
            version.publish(version.manifestDigest(), gitTag, context.principalId());
            versionRepository.update(version);

            // P7: 更新请求状态
            jdbcTemplate.update("UPDATE publish_request SET status = 'PUBLISHED', decided_at = ? WHERE request_id = ?",
                    Instant.now(), requestId);

            // 记录审计事件
            recordAuditEvent(requestId, versionId, context.principalId(),
                    context.traceId(), "PUBLISH_SUCCEEDED", null, "SUCCEEDED");

            LOG.info("version published successfully versionId={} requestId={} tag={}", versionId, requestId, gitTag);

        } catch (Exception e) {
            LOG.error("P6-P7 failed for versionId={}, attempting compensation", versionId, e);

            // 补偿：单次重试 P6+P7（PG 状态更新）
            try {
                LOG.info("compensation: retrying P6-P7 for versionId={}", versionId);

                Version retryVersion = versionRepository.findByVersionId(versionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "compensation: version not found during retry: " + versionId));

                retryVersion.publish(retryVersion.manifestDigest(), gitTag, context.principalId());
                versionRepository.update(retryVersion);

                jdbcTemplate.update("UPDATE publish_request SET status = 'PUBLISHED', decided_at = ? WHERE request_id = ?",
                        Instant.now(), requestId);

                recordAuditEvent(requestId, versionId, context.principalId(),
                        context.traceId(), "PUBLISH_COMPENSATION_RETRY_SUCCEEDED", null, "SUCCEEDED");
                LOG.info("compensation retry succeeded versionId={} requestId={}", versionId, requestId);

            } catch (Exception compensationException) {
                // 补偿也失败 → 标记 SAGA_COMPENSATION_FAILED
                sagaCompensationFailed(requestId, versionId, context.principalId(),
                        context.traceId(), compensationException);
                throw compensationException;
            }
        }
    }

    private void markFailed(String requestId, String versionId, String principalId) {
        jdbcTemplate.update("UPDATE publish_request SET status = 'FAILED', decided_at = ? WHERE request_id = ?",
                Instant.now(), requestId);
        recordAuditEvent(requestId, versionId, principalId, null,
                "PUBLISH_FAILED", null, "FAILED");
    }

    /**
     * Saga 补偿失败处理。
     * <p>当正向流程和补偿流程均失败时，标记请求为 SAGA_COMPENSATION_FAILED，
     * 触发告警和审计记录，等待人工介入。</p>
     */
    private void sagaCompensationFailed(String requestId, String versionId,
                                        String principalId, String traceId,
                                        Exception cause) {
        LOG.error("SAGA_COMPENSATION_FAILED requestId={} versionId={}", requestId, versionId, cause);

        // 1. 更新请求状态
        try {
            jdbcTemplate.update(
                    "UPDATE publish_request SET status = 'SAGA_COMPENSATION_FAILED', decided_at = ? WHERE request_id = ?",
                    Instant.now(), requestId);
        } catch (Exception ex) {
            LOG.error("Failed to update publish_request to SAGA_COMPENSATION_FAILED requestId={}", requestId, ex);
        }

        // 2. 记录审计事件
        recordAuditEvent(requestId, versionId, principalId, traceId,
                "SAGA_COMPENSATION_FAILED",
                cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                "FAILED");

        // 3. 触发告警
        triggerCompensationAlert(requestId, versionId, traceId, cause);
    }

    /**
     * 触发 Saga 补偿失败告警到 system_alert 表。
     */
    private void triggerCompensationAlert(String requestId, String versionId,
                                          String traceId, Exception cause) {
        try {
            String alertId = "alt_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            String detail = cause.getClass().getSimpleName() + ": " +
                    (cause.getMessage() != null ? cause.getMessage() : "");
            jdbcTemplate.update(
                    "INSERT INTO system_alert (alert_id, alert_type, severity, title, detail, status, fired_at, trace_id) " +
                            "VALUES (?, 'SAGA_COMPENSATION_FAILED', 'CRITICAL', ?, ?, 'FIRING', ?, ?)",
                    alertId,
                    "Publish Saga compensation failed: " + requestId,
                    detail,
                    Instant.now(),
                    traceId != null ? traceId : "");
            LOG.info("compensation alert fired alertId={} requestId={}", alertId, requestId);
        } catch (Exception ex) {
            LOG.error("Failed to trigger compensation alert requestId={}", requestId, ex);
        }
    }

    /**
     * 记录审计事件到 audit_log 表。
     */
    private void recordAuditEvent(String requestId, String versionId,
                                  String principalId, String traceId,
                                  String eventType, String errorCode,
                                  String result) {
        try {
            String auditId = "aud_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            String requestSummary = objectMapper.writeValueAsString(Map.of(
                    "requestId", requestId,
                    "versionId", versionId,
                    "eventType", eventType));

            jdbcTemplate.update(
                    "INSERT INTO audit_log (audit_id, event_type, principal_id, principal_type, action, " +
                            "resource_type, resource_id, result, error_code, request_summary, trace_id, occurred_at) " +
                            "VALUES (?, ?, ?, 'SYSTEM', ?, 'PUBLISH_REQUEST', ?, ?, ?, ?, ?, ?)",
                    auditId, eventType, principalId != null ? principalId : "system",
                    eventType, requestId, result,
                    errorCode, requestSummary,
                    traceId != null ? traceId : "", Instant.now());
        } catch (Exception ex) {
            LOG.error("Failed to record audit event", ex);
        }
    }
}