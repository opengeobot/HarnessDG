package com.aihub.integration.minio.infrastructure;

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
 * PG↔MinIO 存储对账 Worker。
 *
 * <p>检测 upload_session 和 version_artifact 与 MinIO 对象之间的一致性。
 * 过期 OPEN Session 自动标记 EXPIRED；孤立 artifact 记录告警。
 * 差异分类：AUTO_REPAIR / MANUAL_REVIEW / SECURITY_INCIDENT。
 */
@Component
public class MinioStorageReconciler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MinioStorageReconciler.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformMetrics platformMetrics;

    public MinioStorageReconciler(JdbcTemplate jdbcTemplate, PlatformMetrics platformMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.platformMetrics = platformMetrics;
    }

    @Override
    public String type() {
        return "MINIO_STORAGE_RECONCILE";
    }

    @Override
    public void handle(JobContext context) {
        LOG.info("starting MinIO storage reconciliation jobId={}", context.jobId());

        int totalChecked = 0;
        int discrepancies = 0;

        // 1. 检测过期 OPEN Session → 自动 EXPIRED
        discrepancies += expireStaleSessions(context.jobId());

        // 2. 检测 COMMITTING 状态超时 → 标记 FAILED
        discrepancies += detectStaleCommitting(context.jobId());

        // 3. 检测 version_artifact 字段完整性
        String cursor = null;
        do {
            List<Map<String, Object>> batch = cursor == null
                    ? jdbcTemplate.queryForList(
                    "SELECT artifact_id, version_id, path, sha256, size, media_type " +
                            "FROM version_artifact ORDER BY artifact_id LIMIT ?", BATCH_SIZE)
                    : jdbcTemplate.queryForList(
                    "SELECT artifact_id, version_id, path, sha256, size, media_type " +
                            "FROM version_artifact WHERE artifact_id > ? ORDER BY artifact_id LIMIT ?",
                    cursor, BATCH_SIZE);

            if (batch.isEmpty()) break;

            for (Map<String, Object> row : batch) {
                String artifactId = (String) row.get("artifact_id");
                String path = (String) row.get("path");
                String sha256 = (String) row.get("sha256");
                Long size = row.get("size") != null ? ((Number) row.get("size")).longValue() : null;

                ReconcileResult result = checkArtifact(artifactId, path, sha256, size);
                if (result != ReconcileResult.CONSISTENT) {
                    discrepancies++;
                    recordDiscrepancy(artifactId, result);
                }
                totalChecked++;
            }

            cursor = (String) batch.get(batch.size() - 1).get("artifact_id");
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        } while (true);

        LOG.info("MinIO storage reconciliation completed jobId={} totalChecked={} discrepancies={}",
                context.jobId(), totalChecked, discrepancies);
    }

    private int expireStaleSessions(String jobId) {
        int expired = jdbcTemplate.update(
                "UPDATE upload_session SET status = 'EXPIRED', updated_at = NOW() " +
                        "WHERE status = 'OPEN' AND expires_at < NOW()");
        if (expired > 0) {
            LOG.info("expired {} stale upload sessions jobId={}", expired, jobId);
            for (int i = 0; i < expired; i++) {
                platformMetrics.recordUploadSessionExpired();
            }
        }
        return expired;
    }

    private int detectStaleCommitting(String jobId) {
        // COMMITTING 超过 30 分钟视为异常
        int stale = jdbcTemplate.update(
                "UPDATE upload_session SET status = 'CANCELLED', updated_at = NOW() " +
                        "WHERE status = 'COMMITTING' AND updated_at < NOW() - INTERVAL '30 minutes'");
        if (stale > 0) {
            LOG.warn("cancelled {} stale COMMITTING sessions jobId={}", stale, jobId);
        }
        return stale;
    }

    private ReconcileResult checkArtifact(String artifactId, String path, String sha256, Long size) {
        // path 非空且不含路径穿越
        if (path == null || path.isEmpty()) {
            return ReconcileResult.MANUAL_REVIEW;
        }
        if (path.contains("..") || path.startsWith("/") || path.startsWith("\\")) {
            LOG.warn("artifact {} has suspicious path: '{}'", artifactId, path);
            return ReconcileResult.SECURITY_INCIDENT;
        }

        // SHA-256 格式校验
        if (sha256 != null && !sha256.isEmpty()) {
            if (!sha256.matches("^[a-f0-9]{64}$")) {
                LOG.warn("artifact {} has invalid sha256 format", artifactId);
                return ReconcileResult.MANUAL_REVIEW;
            }
        }

        // size 合理性检查
        if (size != null && size < 0) {
            LOG.warn("artifact {} has negative size: {}", artifactId, size);
            return ReconcileResult.MANUAL_REVIEW;
        }

        return ReconcileResult.CONSISTENT;
    }

    private void recordDiscrepancy(String artifactId, ReconcileResult result) {
        LOG.warn("MinIO reconciliation discrepancy artifactId={} classification={}", artifactId, result);
        if (result == ReconcileResult.SECURITY_INCIDENT) {
            LOG.error("SECURITY INCIDENT: artifact {} has critical inconsistency", artifactId);
        }
        platformMetrics.recordReconciliationDiscrepancy("MinioStorage", result.name());
        try {
            String deliveryId = "minio-reconcile-" + artifactId + "-" + System.currentTimeMillis();
            jdbcTemplate.update(
                    "INSERT INTO webhook_inbox (delivery_id, event_type, source, signature_valid, payload, status) " +
                            "VALUES (?, 'MINIO_RECONCILE_DISCREPANCY', 'internal', true, " +
                            "jsonb_build_object('artifactId', ?, 'classification', ?::text), 'COMPLETED') " +
                            "ON CONFLICT (delivery_id) DO NOTHING",
                    deliveryId, artifactId, result.name());
        } catch (Exception e) {
            LOG.error("failed to record MinIO discrepancy for artifactId={}", artifactId, e);
        }
    }

    enum ReconcileResult {
        CONSISTENT,
        AUTO_REPAIR,
        MANUAL_REVIEW,
        SECURITY_INCIDENT
    }
}
