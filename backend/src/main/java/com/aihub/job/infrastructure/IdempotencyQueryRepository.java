/*
 * 功能: 幂等记录 JDBC 只读仓储——查询 api_idempotency 已完成记录。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.job.domain.IdempotencyQueryPort;
import com.aihub.job.domain.IdempotencyRecordSummary;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 幂等记录只读查询仓储。
 */
@Repository
public class IdempotencyQueryRepository implements IdempotencyQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<IdempotencyRecordSummary> listRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT idempotency_key, principal_id, method, path, request_digest, created_at "
                        + "FROM api_idempotency "
                        + "WHERE status = 'COMPLETED' "
                        + "ORDER BY created_at DESC "
                        + "LIMIT ?",
                (rs, rowNum) -> new IdempotencyRecordSummary(
                        rs.getString("idempotency_key"),
                        rs.getString("principal_id"),
                        rs.getString("method"),
                        rs.getString("path"),
                        rs.getString("request_digest"),
                        rs.getTimestamp("created_at").toInstant()),
                limit);
    }
}
