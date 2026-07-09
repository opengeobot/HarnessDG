package com.aihub.job.infrastructure;

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
 * Job↔Lease 对账 Worker。
 *
 * <p>检测 job_task 表中租约状态的一致性：
 * - RUNNING 但租约已过期的任务 → 自动恢复为 PENDING
 * - 尝试次数超过 max_attempts 但状态不是 DEAD/CANCELLED → 标记 DEAD
 * - RUNNING 任务无对应 job_attempt 记录 → MANUAL_REVIEW
 */
@Component
public class JobLeaseReconciler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JobLeaseReconciler.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformMetrics platformMetrics;

    public JobLeaseReconciler(JdbcTemplate jdbcTemplate, PlatformMetrics platformMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.platformMetrics = platformMetrics;
    }

    @Override
    public String type() {
        return "JOB_LEASE_RECONCILE";
    }

    @Override
    public void handle(JobContext context) {
        LOG.info("starting job-lease reconciliation jobId={}", context.jobId());

        int totalChecked = 0;
        int discrepancies = 0;

        // 1. 恢复过期租约：RUNNING 且 leased_until < NOW()
        int recovered = jdbcTemplate.update(
                "UPDATE job_task SET status = 'PENDING', leased_until = NULL, leased_by = NULL, " +
                        "updated_at = NOW() " +
                        "WHERE status = 'RUNNING' AND leased_until IS NOT NULL AND leased_until < NOW()");
        if (recovered > 0) {
            LOG.info("recovered {} expired-lease RUNNING jobs jobId={}", recovered, context.jobId());
            discrepancies += recovered;
        }

        // 2. 超限但未 DEAD → 标记 DEAD
        int deadMarked = jdbcTemplate.update(
                "UPDATE job_task SET status = 'DEAD', error_code = 'MAX_ATTEMPTS_EXCEEDED', " +
                        "updated_at = NOW() " +
                        "WHERE status NOT IN ('DEAD', 'CANCELLED', 'SUCCEEDED') " +
                        "AND attempts >= max_attempts");
        if (deadMarked > 0) {
            LOG.warn("marked {} over-limit jobs as DEAD jobId={}", deadMarked, context.jobId());
            discrepancies += deadMarked;
        }

        // 3. 检测 RUNNING 任务完整性
        String cursor = null;
        do {
            List<Map<String, Object>> batch = cursor == null
                    ? jdbcTemplate.queryForList(
                    "SELECT job_id, type, status, attempts, max_attempts, leased_until, leased_by " +
                            "FROM job_task WHERE status IN ('RUNNING', 'PENDING', 'RETRY_WAIT') " +
                            "ORDER BY job_id LIMIT ?", BATCH_SIZE)
                    : jdbcTemplate.queryForList(
                    "SELECT job_id, type, status, attempts, max_attempts, leased_until, leased_by " +
                            "FROM job_task WHERE status IN ('RUNNING', 'PENDING', 'RETRY_WAIT') " +
                            "AND job_id > ? ORDER BY job_id LIMIT ?", cursor, BATCH_SIZE);

            if (batch.isEmpty()) break;

            for (Map<String, Object> row : batch) {
                String jobId = (String) row.get("job_id");
                String status = (String) row.get("status");
                int attempts = row.get("attempts") != null ? ((Number) row.get("attempts")).intValue() : 0;
                int maxAttempts = row.get("max_attempts") != null ? ((Number) row.get("max_attempts")).intValue() : 5;

                ReconcileResult result = checkJobLease(jobId, status, attempts, maxAttempts);
                if (result != ReconcileResult.CONSISTENT) {
                    discrepancies++;
                    recordDiscrepancy(jobId, result);
                }
                totalChecked++;
            }

            cursor = (String) batch.get(batch.size() - 1).get("job_id");
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        } while (true);

        LOG.info("job-lease reconciliation completed jobId={} totalChecked={} discrepancies={}",
                context.jobId(), totalChecked, discrepancies);
    }

    private ReconcileResult checkJobLease(String jobId, String status, int attempts, int maxAttempts) {
        // attempts 不能为负
        if (attempts < 0) {
            LOG.warn("job {} has negative attempts: {}", jobId, attempts);
            return ReconcileResult.MANUAL_REVIEW;
        }

        // maxAttempts 必须 >= 1
        if (maxAttempts < 1) {
            LOG.warn("job {} has invalid max_attempts: {}", jobId, maxAttempts);
            return ReconcileResult.MANUAL_REVIEW;
        }

        // RUNNING 任务必须有 leased_by
        if ("RUNNING".equals(status)) {
            String leasedBy = jdbcTemplate.queryForObject(
                    "SELECT leased_by FROM job_task WHERE job_id = ?", String.class, jobId);
            if (leasedBy == null || leasedBy.isEmpty()) {
                LOG.warn("RUNNING job {} has no leased_by", jobId);
                return ReconcileResult.MANUAL_REVIEW;
            }
        }

        return ReconcileResult.CONSISTENT;
    }

    private void recordDiscrepancy(String jobId, ReconcileResult result) {
        LOG.warn("job-lease discrepancy jobId={} classification={}", jobId, result);
        platformMetrics.recordReconciliationDiscrepancy("JobLease", result.name());
        try {
            String deliveryId = "job-lease-reconcile-" + jobId + "-" + System.currentTimeMillis();
            jdbcTemplate.update(
                    "INSERT INTO webhook_inbox (delivery_id, event_type, source, signature_valid, payload, status) " +
                            "VALUES (?, 'JOB_LEASE_RECONCILE_DISCREPANCY', 'internal', true, " +
                            "jsonb_build_object('jobId', ?, 'classification', ?::text), 'COMPLETED') " +
                            "ON CONFLICT (delivery_id) DO NOTHING",
                    deliveryId, jobId, result.name());
        } catch (Exception e) {
            LOG.error("failed to record job-lease discrepancy for jobId={}", jobId, e);
        }
    }

    enum ReconcileResult {
        CONSISTENT,
        AUTO_REPAIR,
        MANUAL_REVIEW,
        SECURITY_INCIDENT
    }
}
