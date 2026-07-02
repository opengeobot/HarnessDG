/*
 * 功能: 通知 JDBC 仓储——站内通知创建、游标查询与标记已读。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import com.aihub.notification.domain.Notification;
import com.aihub.notification.domain.NotificationRepository;
import com.aihub.notification.domain.NotificationSeverity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 通知 JDBC 仓储。
 *
 * <p>查询按 {@code created_at, id} 降序键集游标（最新优先）。{@code parameters} 以 JSONB 写入。
 */
@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Notification> MAPPER = (rs, rowNum) -> mapNotification(rs);

    private static Notification mapNotification(ResultSet rs) throws SQLException {
        return new Notification(
                rs.getLong("id"),
                rs.getString("notification_id"),
                rs.getString("principal_id"),
                rs.getString("event_type"),
                rs.getString("i18n_key"),
                rs.getString("parameters"),
                NotificationSeverity.valueOf(rs.getString("severity")),
                rs.getShort("read"),
                getInstant(rs, "created_at"),
                getInstant(rs, "read_at"),
                rs.getInt("row_version"));
    }

    private static Instant getInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    @Override
    public void insert(Notification notification) {
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO notification (notification_id, principal_id, event_type, i18n_key,
                        parameters, severity, read, created_at, read_at, row_version)
                    VALUES (?,?,?,?,?::jsonb,?,0,?,NULL,0)
                    """);
            ps.setString(1, notification.notificationId());
            ps.setString(2, notification.principalId());
            ps.setString(3, notification.eventType());
            ps.setString(4, notification.i18nKey());
            if (notification.parameters() == null) {
                ps.setNull(5, java.sql.Types.OTHER);
            } else {
                ps.setObject(5, notification.parameters(), java.sql.Types.OTHER);
            }
            ps.setString(6, notification.severity().name());
            ps.setTimestamp(7, Timestamp.from(notification.createdAt()));
            return ps;
        });
    }

    @Override
    public Notification findByNotificationId(String notificationId) {
        List<Notification> rows = jdbcTemplate.query(
                "SELECT * FROM notification WHERE notification_id = ?", MAPPER, notificationId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<Notification> listByPrincipal(String principalId, boolean unreadOnly,
                                              Instant cursorTime, Long cursorId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM notification WHERE principal_id = ?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(principalId);
        if (unreadOnly) {
            sql.append(" AND read = 0");
        }
        if (cursorTime != null && cursorId != null) {
            sql.append(" AND (created_at, id) < (?, ?)");
            args.add(Timestamp.from(cursorTime));
            args.add(cursorId);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    @Override
    public boolean markRead(String notificationId, String principalId, Instant now) {
        int updated = jdbcTemplate.update(
                "UPDATE notification SET read = 1, read_at = ?, row_version = row_version + 1 "
                        + "WHERE notification_id = ? AND principal_id = ? AND read = 0",
                Timestamp.from(now), notificationId, principalId);
        return updated > 0;
    }
}
