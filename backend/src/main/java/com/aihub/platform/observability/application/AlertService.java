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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 系统告警应用服务。
 *
 * <p>定时检查平台指标并生成告警记录（FIRING / RESOLVED）。
 * 检查项：DEAD 投递数、Outbox 积压数、认证失败率。
 */
@Service
public class AlertService {

    private static final Logger LOG = LoggerFactory.getLogger(AlertService.class);
    private static final int DEFAULT_LIMIT = 100;

    /** DEAD 投递告警阈值 */
    private static final long DEAD_DELIVERY_THRESHOLD = 5;
    /** Outbox 积压告警阈值 */
    private static final long OUTBOX_BACKLOG_THRESHOLD = 100;

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

    /** 定时告警检查（每 5 分钟）。 */
    @Scheduled(fixedDelayString = "${aihub.alert.check-interval:300000}")
    public void checkAlerts() {
        try {
            checkDeadDeliveries();
            checkOutboxBacklog();
        } catch (Exception e) {
            LOG.warn("alert check failed", e);
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
