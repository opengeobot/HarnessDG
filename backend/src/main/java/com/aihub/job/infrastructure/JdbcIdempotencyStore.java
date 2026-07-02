/*
 * 功能: 幂等存储 JDBC 持久化实现，基于 api_idempotency 表，INSERT ON CONFLICT 处理并发重复键。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencyRecord;
import com.aihub.shared.idempotency.IdempotencyStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等存储 JDBC 实现。
 *
 * <p>基于 {@code api_idempotency} 表：{@code find} 按 idempotency_key 查询命中记录；
 * {@code save} 以 {@code INSERT ON CONFLICT (idempotency_key) DO NOTHING} 写入，并发下已存在则回查
 * 实际生效记录，保证"恰好一次"语义。response_payload 以 JSONB 承载 {status, body}。
 */
@Component
public class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcIdempotencyStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<IdempotencyRecord> find(IdempotencyKey key) {
        List<IdempotencyRecord> rows = jdbcTemplate.query(
                "SELECT response_payload, request_digest, created_at FROM api_idempotency "
                        + "WHERE idempotency_key = ? AND status = 'COMPLETED'",
                (rs, rowNum) -> {
                    String payload = rs.getString("response_payload");
                    String digest = rs.getString("request_digest");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    int status = 0;
                    String body = null;
                    if (payload != null) {
                        try {
                            JsonNode node = objectMapper.readTree(payload);
                            status = node.path("status").asInt(0);
                            body = node.path("body").isNull() ? null : node.path("body").asText(null);
                        } catch (Exception parseEx) {
                            // 损坏的 payload 视为未命中
                        }
                    }
                    return new IdempotencyRecord(key, digest, status, body, createdAt);
                },
                key.key());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", record.responseStatus());
        if (record.responseBody() == null) {
            payload.putNull("body");
        } else {
            payload.put("body", record.responseBody());
        }
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            int inserted = jdbcTemplate.update("""
                    INSERT INTO api_idempotency (idempotency_key, method, path, request_digest, status,
                        response_payload, created_at, completed_at)
                    VALUES (?,?,?,?, 'COMPLETED', ?::jsonb, now(), now())
                    ON CONFLICT (idempotency_key) DO NOTHING
                    """,
                    record.key().key(), record.key().method(), record.key().path(),
                    record.requestFingerprint(), payloadJson);
            if (inserted == 0) {
                // 并发下已存在同键记录：回查实际生效记录返回。
                return find(record.key()).orElse(record);
            }
            return record;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist idempotency record", ex);
        }
    }
}
