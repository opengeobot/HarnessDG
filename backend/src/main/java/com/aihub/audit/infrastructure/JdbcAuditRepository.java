/*
 * 功能: 审计 JDBC 仓储——追加写（无 UPDATE/DELETE），按 occurred_at,id 降序键集游标查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.audit.infrastructure;

import com.aihub.audit.domain.AuditRecord;
import com.aihub.audit.domain.AuditRepository;
import com.aihub.audit.domain.AuditResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 审计 JDBC 仓储。
 *
 * <p><b>仅追加写</b>：{@link #append} 单条 INSERT；无任何 UPDATE/DELETE 方法，应用层无更新路径。
 * 查询按 {@code occurred_at, id} 降序键集游标（最新优先）。{@code request_summary} 以 JSONB 写入。
 */
@Repository
public class JdbcAuditRepository implements AuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AuditRecord> MAPPER = (rs, rowNum) -> mapRecord(rs);

    private static AuditRecord mapRecord(ResultSet rs) throws SQLException {
        Long duration = rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms");
        return new AuditRecord(
                rs.getLong("id"),
                rs.getString("audit_id"),
                rs.getString("event_type"),
                rs.getString("principal_id"),
                rs.getString("principal_type"),
                rs.getString("action"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("scope_type"),
                rs.getString("scope_id"),
                AuditResult.valueOf(rs.getString("result")),
                rs.getString("error_code"),
                rs.getString("request_summary"),
                rs.getString("trace_id"),
                rs.getString("request_id"),
                duration,
                rs.getTimestamp("occurred_at").toInstant());
    }

    @Override
    public void append(AuditRecord record) {
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO audit_log (audit_id, event_type, principal_id, principal_type, action,
                        resource_type, resource_id, scope_type, scope_id, result, error_code,
                        request_summary, trace_id, request_id, duration_ms, occurred_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """);
            ps.setString(1, record.auditId());
            ps.setString(2, record.eventType());
            ps.setString(3, record.principalId());
            ps.setString(4, record.principalType());
            ps.setString(5, record.action());
            ps.setString(6, record.resourceType());
            ps.setString(7, record.resourceId());
            ps.setString(8, record.scopeType());
            ps.setString(9, record.scopeId());
            ps.setString(10, record.result().name());
            ps.setString(11, record.errorCode());
            if (record.requestSummary() == null) {
                ps.setNull(12, java.sql.Types.OTHER);
            } else {
                ps.setObject(12, record.requestSummary(), java.sql.Types.OTHER);
            }
            ps.setString(13, record.traceId());
            ps.setString(14, record.requestId());
            if (record.durationMs() == null) {
                ps.setNull(15, java.sql.Types.BIGINT);
            } else {
                ps.setLong(15, record.durationMs());
            }
            ps.setTimestamp(16, Timestamp.from(record.occurredAt()));
            return ps;
        });
    }

    @Override
    public List<AuditRecord> list(String principalId, String action, String resourceId,
                                  Instant cursorTime, Long cursorId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (principalId != null && !principalId.isBlank()) {
            sql.append(" AND principal_id = ?");
            args.add(principalId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            args.add(action);
        }
        if (resourceId != null && !resourceId.isBlank()) {
            sql.append(" AND resource_id = ?");
            args.add(resourceId);
        }
        if (cursorTime != null && cursorId != null) {
            sql.append(" AND (occurred_at, id) < (?, ?)");
            args.add(Timestamp.from(cursorTime));
            args.add(cursorId);
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    @Override
    public List<AuditRecord> search(String eventType, String principalId, String resourceType, String resourceId,
                                    Instant createdAfter, Instant createdBefore,
                                    Instant cursorTime, Long cursorId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND event_type = ?");
            args.add(eventType);
        }
        if (principalId != null && !principalId.isBlank()) {
            sql.append(" AND principal_id = ?");
            args.add(principalId);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            sql.append(" AND resource_type = ?");
            args.add(resourceType);
        }
        if (resourceId != null && !resourceId.isBlank()) {
            sql.append(" AND resource_id = ?");
            args.add(resourceId);
        }
        if (createdAfter != null) {
            sql.append(" AND occurred_at >= ?");
            args.add(Timestamp.from(createdAfter));
        }
        if (createdBefore != null) {
            sql.append(" AND occurred_at < ?");
            args.add(Timestamp.from(createdBefore));
        }
        if (cursorTime != null && cursorId != null) {
            sql.append(" AND (occurred_at, id) < (?, ?)");
            args.add(Timestamp.from(cursorTime));
            args.add(cursorId);
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }
}
