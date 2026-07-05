/*
 * 功能: 基于 PostgreSQL api_idempotency 表的幂等存储实现。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencyRecord;
import com.aihub.shared.idempotency.IdempotencyStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC 幂等存储。
 *
 * <p>使用 {@code api_idempotency} 表持久化首次执行结果；{@code INSERT ON CONFLICT DO NOTHING}
 * 保证并发下只有一个请求写入成功，其余读取已有记录。
 */
@Repository
public class JdbcIdempotencyStore implements IdempotencyStore {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcIdempotencyStore.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcIdempotencyStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<IdempotencyRecord> find(IdempotencyKey key) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("key", key.key())
                .addValue("principalId", key.principalId())
                .addValue("method", key.method())
                .addValue("path", key.path());

        var results = jdbc.query(
                "SELECT idempotency_key, principal_id, method, path, request_digest, " +
                        "response_payload, created_at " +
                        "FROM api_idempotency " +
                        "WHERE idempotency_key = :key " +
                        "  AND principal_id = :principalId " +
                        "  AND method = :method " +
                        "  AND path = :path " +
                        "  AND status = 'COMPLETED'",
                params,
                (rs, rowNum) -> {
                    IdempotencyKey k = new IdempotencyKey(
                            rs.getString("idempotency_key"),
                            rs.getString("principal_id"),
                            rs.getString("method"),
                            rs.getString("path"));
                    Map<String, Object> payload = readPayload(rs.getString("response_payload"));
                    int status = payload.containsKey("status")
                            ? ((Number) payload.get("status")).intValue() : 200;
                    String body = payload.containsKey("body")
                            ? payload.get("body").toString() : "";
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    return new IdempotencyRecord(k, rs.getString("request_digest"),
                            status, body, createdAt);
                });

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        String payloadJson = writePayload(record.responseStatus(), record.responseBody());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("key", record.key().key())
                .addValue("principalId", record.key().principalId())
                .addValue("method", record.key().method())
                .addValue("path", record.key().path())
                .addValue("digest", record.requestFingerprint())
                .addValue("payload", payloadJson)
                .addValue("now", Instant.now());

        try {
            jdbc.update(
                    "INSERT INTO api_idempotency " +
                            "(idempotency_key, principal_id, method, path, request_digest, " +
                            " status, response_payload, completed_at) " +
                            "VALUES (:key, :principalId, :method, :path, :digest, " +
                            " 'COMPLETED', :payload::jsonb, :now) " +
                            "ON CONFLICT (idempotency_key) DO NOTHING",
                    params);
        } catch (DuplicateKeyException e) {
            LOG.debug("idempotency key already exists, concurrent save: key={}", record.key().key());
        }

        // 返回已有记录（可能是并发写入的另一条）
        return find(record.key()).orElse(record);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            LOG.warn("failed to parse idempotency response payload", e);
            return Map.of();
        }
    }

    private String writePayload(int status, String body) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", status, "body", body != null ? body : ""));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize idempotency payload", e);
        }
    }
}
