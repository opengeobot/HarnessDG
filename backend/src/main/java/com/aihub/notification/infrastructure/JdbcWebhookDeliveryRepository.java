/*
 * 功能: Webhook 投递 JDBC 仓储——创建、领取重试与状态更新。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import com.aihub.notification.domain.WebhookDelivery;
import com.aihub.notification.domain.WebhookDeliveryRepository;
import com.aihub.notification.domain.WebhookDeliveryStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Webhook 投递 JDBC 仓储。
 */
@Repository
public class JdbcWebhookDeliveryRepository implements WebhookDeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWebhookDeliveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<WebhookDelivery> MAPPER = (rs, rowNum) -> mapDelivery(rs);

    private static WebhookDelivery mapDelivery(ResultSet rs) throws SQLException {
        Integer lastResponseCode = rs.getObject("last_response_code") == null
                ? null : rs.getInt("last_response_code");
        return new WebhookDelivery(
                rs.getLong("id"),
                rs.getString("delivery_id"),
                rs.getString("event_id"),
                rs.getString("target_url"),
                rs.getString("payload"),
                rs.getString("signature_header"),
                WebhookDeliveryStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                lastResponseCode,
                rs.getString("last_error"),
                getInstant(rs, "next_retry_at"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static Instant getInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    @Override
    public void insert(WebhookDelivery delivery) {
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO webhook_delivery (delivery_id, event_id, target_url, payload,
                        signature_header, status, attempts, last_response_code, last_error,
                        next_retry_at, created_at, updated_at)
                    VALUES (?,?,?,?,?::jsonb,?,?,NULL,NULL,NULL,?,?)
                    """);
            ps.setString(1, delivery.deliveryId());
            ps.setString(2, delivery.eventId());
            ps.setString(3, delivery.targetUrl());
            if (delivery.payload() == null) {
                ps.setNull(4, java.sql.Types.OTHER);
            } else {
                ps.setObject(4, delivery.payload(), java.sql.Types.OTHER);
            }
            ps.setString(5, delivery.signatureHeader());
            ps.setString(6, delivery.status().name());
            ps.setInt(7, delivery.attempts());
            ps.setTimestamp(8, Timestamp.from(delivery.createdAt()));
            ps.setTimestamp(9, Timestamp.from(delivery.updatedAt()));
            return ps;
        });
    }

    @Override
    public List<WebhookDelivery> claimRetryable(Instant now, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM webhook_delivery
                WHERE status IN ('PENDING', 'FAILED') AND (next_retry_at IS NULL OR next_retry_at <= ?)
                ORDER BY created_at
                LIMIT ?
                """, MAPPER, Timestamp.from(now), limit);
    }

    @Override
    public void markDelivered(String deliveryId, int httpStatus, Instant now) {
        jdbcTemplate.update(
                "UPDATE webhook_delivery SET status = 'DELIVERED', last_response_code = ?, "
                        + "next_retry_at = NULL, updated_at = ? WHERE delivery_id = ?",
                httpStatus, Timestamp.from(now), deliveryId);
    }

    @Override
    public void markFailed(String deliveryId, int attempts, Integer httpStatus, String error,
                            Instant nextRetryAt, Instant now) {
        jdbcTemplate.update(
                "UPDATE webhook_delivery SET status = 'FAILED', attempts = ?, last_response_code = ?, "
                        + "last_error = ?, next_retry_at = ?, updated_at = ? WHERE delivery_id = ?",
                attempts, httpStatus, error, Timestamp.from(nextRetryAt), Timestamp.from(now), deliveryId);
    }

    @Override
    public void markDead(String deliveryId, int attempts, Integer httpStatus, String error, Instant now) {
        jdbcTemplate.update(
                "UPDATE webhook_delivery SET status = 'DEAD', attempts = ?, last_response_code = ?, "
                        + "last_error = ?, next_retry_at = NULL, updated_at = ? WHERE delivery_id = ?",
                attempts, httpStatus, error, Timestamp.from(now), deliveryId);
    }

    @Override
    public List<WebhookDelivery> listRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM webhook_delivery ORDER BY created_at DESC LIMIT ?",
                MAPPER, limit);
    }

    @Override
    public long countByStatus(WebhookDeliveryStatus status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_delivery WHERE status = ?", Long.class, status.name());
        return count != null ? count : 0L;
    }

    @Override
    public boolean resetForRetry(String deliveryId, Instant now) {
        int rows = jdbcTemplate.update(
                "UPDATE webhook_delivery SET status = 'PENDING', next_retry_at = NULL, updated_at = ? "
                        + "WHERE delivery_id = ? AND status IN ('FAILED', 'DEAD')",
                Timestamp.from(now), deliveryId);
        return rows > 0;
    }
}
