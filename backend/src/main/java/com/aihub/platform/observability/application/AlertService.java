/*
 * 功能: 系统告警应用服务——聚合告警查询与定时检查。
 * 时间: 2026-07-06
 * 作者: AxeXie
 */
package com.aihub.platform.observability.application;

import com.aihub.notification.domain.OutboxRepository;
import com.aihub.notification.domain.WebhookDeliveryRepository;
import com.aihub.notification.domain.WebhookDeliveryStatus;
import com.aihub.platform.observability.domain.SystemAlert;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aihub.shared.error.NotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 系统告警应用服务。
 *
 * <p>定时检查平台指标并生成告警记录（FIRING / RESOLVED）。
 * 检查项：DEAD Job 数、DEAD 投递数、Outbox 积压数。
 */
@Service
public class AlertService {

    private static final Logger LOG = LoggerFactory.getLogger(AlertService.class);
    private static final int DEFAULT_LIMIT = 100;

    /** DEAD Job 告警阈值（与 Prometheus JobDead 规则对齐：&gt; 0 即告警） */
    private static final long DEAD_JOB_THRESHOLD = 1;
    /** DEAD 投递告警阈值 */
    private static final long DEAD_DELIVERY_THRESHOLD = 5;
    /** Outbox 积压告警阈值 */
    private static final long OUTBOX_BACKLOG_THRESHOLD = 100;

    private static final String ALERT_STORAGE_QUOTA = "STORAGE_QUOTA_WARNING";
    private static final String ALERT_DEPENDENCY_UNHEALTHY = "SYSTEM_DEPENDENCY_UNHEALTHY";

    private final JdbcTemplate jdbcTemplate;
    private final OutboxRepository outboxRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public AlertService(JdbcTemplate jdbcTemplate,
                         OutboxRepository outboxRepository,
                         WebhookDeliveryRepository deliveryRepository,
                         IdGenerator idGenerator, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxRepository = outboxRepository;
        this.deliveryRepository = deliveryRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** 查询告警列表（按触发时间降序）。 */
    public List<SystemAlert> listAlerts(int limit) {
        int effective = Math.min(Math.max(limit, 1), 200);
        return jdbcTemplate.query(
                "SELECT * FROM system_alert ORDER BY fired_at DESC LIMIT ?",
                ALERT_MAPPER, effective);
    }

    /** 查询当前活跃告警（status = FIRING）。 */
    public List<SystemAlert> listFiringAlerts() {
        return jdbcTemplate.query(
                "SELECT * FROM system_alert WHERE status = 'FIRING' ORDER BY fired_at DESC",
                ALERT_MAPPER);
    }

    /**
     * 人工确认告警：按 alertId 标记为 RESOLVED。
     *
     * @return 更新后的告警
     */
    public SystemAlert acknowledge(String alertId) {
        Instant now = clock.instant();
        int rows = jdbcTemplate.update(
                "UPDATE system_alert SET status = 'RESOLVED', resolved_at = ? "
                        + "WHERE alert_id = ? AND status = 'FIRING'",
                Timestamp.from(now), alertId);
        if (rows == 0) {
            throw new NotFoundException("alert not found or already resolved: " + alertId);
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM system_alert WHERE alert_id = ?",
                    ALERT_MAPPER, alertId);
        } catch (EmptyResultDataAccessException ex) {
            throw new NotFoundException("alert not found: " + alertId);
        }
    }

    /**
     * 存储配额超阈值时触发告警（幂等：同类 FIRING 不重复插入）。
     */
    public void emitStorageQuotaWarning(long usedBytes, long quotaBytes, double usageRatio) {
        if (hasFiringAlert(ALERT_STORAGE_QUOTA)) {
            return;
        }
        fireAlert(ALERT_STORAGE_QUOTA, "WARNING",
                "Storage quota warning",
                String.format("Used %d bytes of %d bytes (%.1f%%)", usedBytes, quotaBytes, usageRatio * 100),
                "storage_usage_ratio", (double) quotaWarningThresholdRatio(), usageRatio);
    }

    /**
     * 依赖不可用时触发告警（幂等：按依赖名去重）。
     */
    public void emitDependencyUnhealthy(String dependency, String status, Long latencyMs) {
        String alertType = ALERT_DEPENDENCY_UNHEALTHY + ":" + dependency;
        if (hasFiringAlert(alertType)) {
            return;
        }
        fireAlert(alertType, "CRITICAL",
                "System dependency unhealthy: " + dependency,
                String.format("dependency=%s status=%s latencyMs=%s", dependency, status, latencyMs),
                "dependency_health", null, null);
    }

    /** 依赖恢复时解除对应告警。 */
    public void resolveDependencyUnhealthy(String dependency) {
        resolveAlert(ALERT_DEPENDENCY_UNHEALTHY + ":" + dependency, 0);
    }

    private double quotaWarningThresholdRatio() {
        return 0.8;
    }

    /** 定时告警检查（每 5 分钟）。 */
    @Scheduled(fixedDelayString = "${aihub.alert.check-interval:300000}")
    public void checkAlerts() {
        try {
            checkDeadJobs();
            checkDeadDeliveries();
            checkOutboxBacklog();
        } catch (Exception e) {
            LOG.warn("alert check failed", e);
        }
    }

    /** 检查 job_task 中 DEAD 状态任务并 upsert {@code DEAD_JOB} 应用内告警。 */
    void checkDeadJobs() {
        Long deadCountObj = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_task WHERE status = 'DEAD'", Long.class);
        long deadCount = deadCountObj != null ? deadCountObj : 0L;
        boolean hasFiringAlert = hasFiringAlert("DEAD_JOB");

        if (deadCount >= DEAD_JOB_THRESHOLD && !hasFiringAlert) {
            fireAlert("DEAD_JOB", "CRITICAL",
                    "Dead jobs detected",
                    String.format("DEAD job count: %d (threshold: %d)", deadCount, DEAD_JOB_THRESHOLD),
                    "aihub_job_dead_count", (double) DEAD_JOB_THRESHOLD, (double) deadCount);
        } else if (deadCount < DEAD_JOB_THRESHOLD && hasFiringAlert) {
            resolveAlert("DEAD_JOB", deadCount);
        }
    }

    private void checkDeadDeliveries() {
        long deadCount = deliveryRepository.countByStatus(WebhookDeliveryStatus.DEAD);
        boolean hasFiringAlert = hasFiringAlert("DEAD_DELIVERY");

        if (deadCount >= DEAD_DELIVERY_THRESHOLD && !hasFiringAlert) {
            fireAlert("DEAD_DELIVERY", "WARNING",
                    "Dead webhook deliveries exceed threshold",
                    String.format("DEAD delivery count: %d (threshold: %d)", deadCount, DEAD_DELIVERY_THRESHOLD),
                    "aihub_webhook_deliveries_total", (double) DEAD_DELIVERY_THRESHOLD, (double) deadCount);
        } else if (deadCount < DEAD_DELIVERY_THRESHOLD && hasFiringAlert) {
            resolveAlert("DEAD_DELIVERY", deadCount);
        }
    }

    private void checkOutboxBacklog() {
        long pendingCount = outboxRepository.countPending();
        boolean hasFiringAlert = hasFiringAlert("OUTBOX_BACKLOG");

        if (pendingCount >= OUTBOX_BACKLOG_THRESHOLD && !hasFiringAlert) {
            fireAlert("OUTBOX_BACKLOG", "WARNING",
                    "Outbox backlog exceeds threshold",
                    String.format("Pending outbox events: %d (threshold: %d)", pendingCount, OUTBOX_BACKLOG_THRESHOLD),
                    "outbox_pending_count", (double) OUTBOX_BACKLOG_THRESHOLD, (double) pendingCount);
        } else if (pendingCount < OUTBOX_BACKLOG_THRESHOLD && hasFiringAlert) {
            resolveAlert("OUTBOX_BACKLOG", pendingCount);
        }
    }

    private boolean hasFiringAlert(String alertType) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_alert WHERE alert_type = ? AND status = 'FIRING'",
                Long.class, alertType);
        return count != null && count > 0;
    }

    private void fireAlert(String alertType, String severity, String title, String detail,
                            String sourceMetric, Double threshold, Double current) {
        String alertId = idGenerator.generate(IdPrefix.ALERT);
        Instant now = clock.instant();
        jdbcTemplate.update(
                "INSERT INTO system_alert (alert_id, alert_type, severity, title, detail, status, "
                        + "source_metric, threshold_value, current_value, fired_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'FIRING', ?, ?, ?, ?)",
                alertId, alertType, severity, title, detail, sourceMetric, threshold, current,
                Timestamp.from(now));
        LOG.warn("alert fired alertType={} title={}", alertType, title);
    }

    private void resolveAlert(String alertType, double currentValue) {
        Instant now = clock.instant();
        int rows = jdbcTemplate.update(
                "UPDATE system_alert SET status = 'RESOLVED', resolved_at = ?, current_value = ? "
                        + "WHERE alert_type = ? AND status = 'FIRING'",
                Timestamp.from(now), currentValue, alertType);
        if (rows > 0) {
            LOG.info("alert resolved alertType={} rows={}", alertType, rows);
        }
    }

    private static final RowMapper<SystemAlert> ALERT_MAPPER = (rs, rowNum) -> mapAlert(rs);

    private static SystemAlert mapAlert(ResultSet rs) throws SQLException {
        return new SystemAlert(
                rs.getLong("id"),
                rs.getString("alert_id"),
                rs.getString("alert_type"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("detail"),
                rs.getString("status"),
                rs.getString("source_metric"),
                getDouble(rs, "threshold_value"),
                getDouble(rs, "current_value"),
                rs.getTimestamp("fired_at").toInstant(),
                getInstant(rs, "resolved_at"),
                rs.getString("trace_id"));
    }

    private static Double getDouble(ResultSet rs, String col) throws SQLException {
        double val = rs.getDouble(col);
        return rs.wasNull() ? null : val;
    }

    private static Instant getInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }
}
