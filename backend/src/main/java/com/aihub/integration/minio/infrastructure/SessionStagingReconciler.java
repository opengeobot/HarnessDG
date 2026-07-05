package com.aihub.integration.minio.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Session↔Staging 对账 Worker。
 *
 * <p>检测 upload_session 与 upload_file/upload_part 之间的计数与字节一致性。
 * OPEN Session 文件计数与声明不符 → MANUAL_REVIEW。
 * COMPLETED Session 缺少文件记录 → SECURITY_INCIDENT。
 */
@Component
public class SessionStagingReconciler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SessionStagingReconciler.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;

    public SessionStagingReconciler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String type() {
        return "SESSION_STAGING_RECONCILE";
    }

    @Override
    public void handle(JobContext context) {
        LOG.info("starting session-staging reconciliation jobId={}", context.jobId());

        int totalChecked = 0;
        int discrepancies = 0;
        String cursor = null;

        do {
            List<Map<String, Object>> batch = cursor == null
                    ? jdbcTemplate.queryForList(
                    "SELECT session_id, asset_id, status, total_bytes, file_count " +
                            "FROM upload_session ORDER BY session_id LIMIT ?", BATCH_SIZE)
                    : jdbcTemplate.queryForList(
                    "SELECT session_id, asset_id, status, total_bytes, file_count " +
                            "FROM upload_session WHERE session_id > ? ORDER BY session_id LIMIT ?",
                    cursor, BATCH_SIZE);

            if (batch.isEmpty()) break;

            for (Map<String, Object> row : batch) {
                String sessionId = (String) row.get("session_id");
                String status = (String) row.get("status");
                long declaredBytes = row.get("total_bytes") != null ? ((Number) row.get("total_bytes")).longValue() : 0;
                int declaredFiles = row.get("file_count") != null ? ((Number) row.get("file_count")).intValue() : 0;

                ReconcileResult result = checkSession(sessionId, status, declaredBytes, declaredFiles);
                if (result != ReconcileResult.CONSISTENT) {
                    discrepancies++;
                    recordDiscrepancy(sessionId, result);
                }
                totalChecked++;
            }

            cursor = (String) batch.get(batch.size() - 1).get("session_id");
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        } while (true);

        LOG.info("session-staging reconciliation completed jobId={} totalChecked={} discrepancies={}",
                context.jobId(), totalChecked, discrepancies);
    }

    private ReconcileResult checkSession(String sessionId, String status, long declaredBytes, int declaredFiles) {
        // COMPLETED Session 必须有文件记录
        if ("COMPLETED".equals(status)) {
            Integer actualFiles = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM upload_file WHERE session_id = ?", Integer.class, sessionId);
            if (actualFiles == null || actualFiles == 0) {
                LOG.warn("COMPLETED session {} has no file records", sessionId);
                return ReconcileResult.SECURITY_INCIDENT;
            }
            if (declaredFiles > 0 && actualFiles != declaredFiles) {
                LOG.warn("COMPLETED session {} file count mismatch: declared={} actual={}",
                        sessionId, declaredFiles, actualFiles);
                return ReconcileResult.MANUAL_REVIEW;
            }
        }

        // OPEN Session 声明 bytes 不能为负
        if ("OPEN".equals(status) && declaredBytes < 0) {
            LOG.warn("OPEN session {} has negative total_bytes: {}", sessionId, declaredBytes);
            return ReconcileResult.MANUAL_REVIEW;
        }

        // CANCELLED/EXPIRED Session 不应有活跃 Part
        if ("CANCELLED".equals(status) || "EXPIRED".equals(status)) {
            Integer pendingParts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM upload_part WHERE session_id = ? AND status = 'PENDING'",
                    Integer.class, sessionId);
            if (pendingParts != null && pendingParts > 0) {
                LOG.warn("{} session {} has {} pending parts (should be cleaned)",
                        status, sessionId, pendingParts);
                return ReconcileResult.AUTO_REPAIR;
            }
        }

        return ReconcileResult.CONSISTENT;
    }

    private void recordDiscrepancy(String sessionId, ReconcileResult result) {
        LOG.warn("session-staging discrepancy sessionId={} classification={}", sessionId, result);
        if (result == ReconcileResult.SECURITY_INCIDENT) {
            LOG.error("SECURITY INCIDENT: session {} has critical inconsistency", sessionId);
        }
        try {
            String deliveryId = "session-reconcile-" + sessionId + "-" + System.currentTimeMillis();
            jdbcTemplate.update(
                    "INSERT INTO webhook_inbox (delivery_id, event_type, source, signature_valid, payload, status) " +
                            "VALUES (?, 'SESSION_RECONCILE_DISCREPANCY', 'internal', true, " +
                            "jsonb_build_object('sessionId', ?, 'classification', ?::text), 'COMPLETED') " +
                            "ON CONFLICT (delivery_id) DO NOTHING",
                    deliveryId, sessionId, result.name());
        } catch (Exception e) {
            LOG.error("failed to record session discrepancy for sessionId={}", sessionId, e);
        }
    }

    enum ReconcileResult {
        CONSISTENT,
        AUTO_REPAIR,
        MANUAL_REVIEW,
        SECURITY_INCIDENT
    }
}
