package com.modelhub.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 审计查询与导出（04 §4.1 / 07 SEC-05）：仅 platform_admin / platform_auditor 可读；
 * 只追加语义——本服务不含任何更新/删除路径（GOV-002 不可篡改）。cursor 分页按 id DESC
 * （BIGSERIAL 单调，等价 created_at 新→旧）；导出为 NDJSON 流式写入（fetchSize 分批），
 * 禁止整表载入内存。
 */
@Service
public class AuditQueryService {

    /** 契约 AuditLog schema（04 OpenAPI）。 */
    public record AuditLogView(UUID id, String actor, String organization, String action,
                               String resource, String result, String sourceIp, String userAgent,
                               String traceId, String idempotencyKey, Object details,
                               OffsetDateTime createdAt) {}

    /** 分页查询行：内部自增 id 作为游标键，视图对外。 */
    private record IdRow(long id, AuditLogView view) {}

    private static final String BASE_COLUMNS =
            "public_id, actor, organization, action, resource, result, ip, user_agent, "
            + "trace_id, idempotency_key, details, created_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<IdRow> idRowMapper = (rs, i) -> new IdRow(rs.getLong("id"), mapRow(rs));

    public AuditQueryService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** json 模式：cursor 分页（新→旧）+ actor/action/resource 可选过滤。 */
    @Transactional(readOnly = true)
    public CursorResult<AuditLogView> query(CurrentPrincipal actor, CursorQuery cursor,
                                            String actorFilter, String actionFilter, String resourceFilter) {
        requireAuditor(actor);
        long lastId = cursor.lastKey() == Long.MIN_VALUE ? Long.MAX_VALUE : cursor.lastKey();
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, ").append(BASE_COLUMNS)
                .append(" FROM audit_logs WHERE id < ?");
        params.add(lastId);
        appendFilter(sql, params, "actor", actorFilter);
        appendFilter(sql, params, "action", actionFilter);
        appendFilter(sql, params, "resource", resourceFilter);
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(cursor.limit() + 1);
        List<IdRow> all = jdbc.query(sql.toString(), idRowMapper, params.toArray());
        boolean hasMore = all.size() > cursor.limit();
        List<IdRow> pageRows = all.stream().limit(cursor.limit()).toList();
        List<AuditLogView> items = pageRows.stream().map(IdRow::view).toList();
        String next = hasMore && !pageRows.isEmpty()
                ? CursorQuery.encode(pageRows.get(pageRows.size() - 1).id()) : null;
        return new CursorResult<>(items, next);
    }

    /** ndjson 模式：全量流式导出（过滤条件同 query）；行内禁止含敏感串（SEC-05）。 */
    @Transactional(readOnly = true)
    public void exportNdjson(CurrentPrincipal actor, String actorFilter, String actionFilter,
                             String resourceFilter, OutputStream out) {
        requireAuditor(actor);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(BASE_COLUMNS)
                .append(" FROM audit_logs WHERE 1=1");
        appendFilter(sql, params, "actor", actorFilter);
        appendFilter(sql, params, "action", actionFilter);
        appendFilter(sql, params, "resource", resourceFilter);
        sql.append(" ORDER BY id DESC");
        // PreparedStatementCreator + fetchSize：PG 游标式遍历，内存占用与行数无关
        jdbc.query(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql.toString());
            ps.setFetchSize(1000);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps;
        }, (RowCallbackHandler) rs -> writeRow(rs, out));
    }

    /** 写入单行 NDJSON；IO 失败转运行时异常终止流（控制器统一处理）。 */
    private void writeRow(ResultSet rs, OutputStream out) throws SQLException {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(mapRow(rs));
            out.write(bytes);
            out.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void requireAuditor(CurrentPrincipal actor) {
        if (actor == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未认证或凭证已过期");
        }
        if (!actor.isPlatformAdmin() && !actor.isPlatformAuditor()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "需要 platform_admin 或 platform_auditor 角色");
        }
    }

    private static void appendFilter(StringBuilder sql, List<Object> params, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND ").append(column).append(" = ?");
        params.add(value.trim());
    }

    private AuditLogView mapRow(ResultSet rs) throws SQLException {
        Object details = null;
        String detailsRaw = rs.getString("details");
        if (detailsRaw != null) {
            try {
                details = objectMapper.readTree(detailsRaw);
            } catch (IOException e) {
                details = detailsRaw;
            }
        }
        return new AuditLogView(
                rs.getObject("public_id", UUID.class),
                rs.getString("actor"),
                rs.getString("organization"),
                rs.getString("action"),
                rs.getString("resource"),
                rs.getString("result"),
                rs.getString("ip"),
                rs.getString("user_agent"),
                rs.getString("trace_id"),
                rs.getString("idempotency_key"),
                details,
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
