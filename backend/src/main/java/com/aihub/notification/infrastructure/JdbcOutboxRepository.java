/*
 * 功能: Outbox JDBC 仓储——事件原子写入与 FOR UPDATE SKIP LOCKED 领取。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import com.aihub.notification.domain.OutboxEvent;
import com.aihub.notification.domain.OutboxRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Outbox JDBC 仓储。
 *
 * <p>{@link #append} 在业务事务内调用。{@link #claimPending} 以 FOR UPDATE SKIP LOCKED 领取，
 * 保证多投递器不重复领取。{@code payload}/{@code headers} 以 JSONB 写入。
 */
@Repository
public class JdbcOutboxRepository implements OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<OutboxEvent> MAPPER = (rs, rowNum) -> mapEvent(rs);

    private static OutboxEvent mapEvent(ResultSet rs) throws SQLException {
        return new OutboxEvent(
                rs.getLong("id"),
                rs.getString("event_id"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getString("headers"),
                rs.getTimestamp("occurred_at").toInstant(),
                getInstant(rs, "processed_at"),
                rs.getString("trace_id"),
                rs.getString("principal_id"));
    }

    private static Instant getInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    @Override
    public void append(OutboxEvent event) {
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO outbox_event (event_id, aggregate_type, aggregate_id, event_type,
                        payload, headers, occurred_at, processed_at, trace_id, principal_id)
                    VALUES (?,?,?,?,?::jsonb,?::jsonb,?,NULL,?,?)
                    """);
            ps.setString(1, event.eventId());
            ps.setString(2, event.aggregateType());
            ps.setString(3, event.aggregateId());
            ps.setString(4, event.eventType());
            if (event.payload() == null) {
                ps.setNull(5, java.sql.Types.OTHER);
            } else {
                ps.setObject(5, event.payload(), java.sql.Types.OTHER);
            }
            if (event.headers() == null) {
                ps.setNull(6, java.sql.Types.OTHER);
            } else {
                ps.setObject(6, event.headers(), java.sql.Types.OTHER);
            }
            ps.setTimestamp(7, Timestamp.from(event.occurredAt()));
            ps.setString(8, event.traceId());
            ps.setString(9, event.principalId());
            return ps;
        });
    }

    @Override
    public List<OutboxEvent> claimPending(int limit) {
        // FOR UPDATE SKIP LOCKED 领取待投递事件
        return jdbcTemplate.query("""
                UPDATE outbox_event
                SET processed_at = '1970-01-01 00:00:01+00'
                WHERE id IN (
                    SELECT id FROM outbox_event
                    WHERE processed_at IS NULL
                    ORDER BY occurred_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                RETURNING *
                """, MAPPER, limit);
    }

    @Override
    public void markProcessed(String eventId, Instant now) {
        jdbcTemplate.update(
                "UPDATE outbox_event SET processed_at = ? WHERE event_id = ?",
                Timestamp.from(now), eventId);
    }

    @Override
    public List<OutboxEvent> listRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM outbox_event ORDER BY occurred_at DESC LIMIT ?",
                MAPPER, limit);
    }

    @Override
    public long countPending() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE processed_at IS NULL", Long.class);
        return count != null ? count : 0L;
    }
}
