package com.modelhub.shared.idempotency;

import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Idempotency-Key 支持（04 §10）：同 key 同 payload 重放返回首次结果；
 * 同 key 不同 payload 返回 409 IDEMPOTENCY_CONFLICT。表结构见 Flyway V1。
 */
public class JdbcIdempotencyService {

    /** 首次受理或已完成重放的结果。 */
    public record Acquired(boolean replay, Integer recordedStatus, String recordedBody) {}

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcIdempotencyService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String validateHeader(String key) {
        if (key == null || key.isBlank() || key.trim().length() < 8 || key.length() > 128) {
            throw ApiException.badRequest("Idempotency-Key 缺失或长度不在 8..128",
                    List.of(new ApiException.Detail("Idempotency-Key", "invalid_format")));
        }
        return key.trim();
    }

    /**
     * 尝试占位。返回 replay=true 表示已有记录：recordedStatus 为空代表首次请求尚未完成（按 CONFLICT 处理）。
     */
    public Acquired acquire(String scope, String idempotencyKey, String requestHash) {
        Map<String, Object> existing;
        try {
            existing = jdbc.queryForMap(
                    """
                    SELECT request_hash, response_status, response_body
                    FROM idempotency_records
                    WHERE scope = :scope AND idempotency_key = :key
                    """,
                    Map.of("scope", scope, "key", idempotencyKey));
        } catch (EmptyResultDataAccessException e) {
            existing = null;
        }
        if (existing != null) {
            String recordedHash = (String) existing.get("request_hash");
            if (recordedHash != null && !recordedHash.equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "相同 Idempotency-Key 已用于不同请求内容，禁止复用");
            }
            Integer status = (Integer) existing.get("response_status");
            if (status == null) {
                throw new ApiException(ErrorCode.CONFLICT, "相同 Idempotency-Key 的请求正在处理中");
            }
            return new Acquired(true, status, (String) existing.get("response_body"));
        }
        try {
            jdbc.update("""
                    INSERT INTO idempotency_records (scope, idempotency_key, request_hash, created_at)
                    VALUES (:scope, :key, :hash, :now)
                    """,
                    new MapSqlParameterSource()
                            .addValue("scope", scope)
                            .addValue("key", idempotencyKey)
                            .addValue("hash", requestHash)
                            .addValue("now", OffsetDateTime.now()));
            return new Acquired(false, null, null);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.CONFLICT, "相同 Idempotency-Key 的请求正在并发处理中");
        }
    }

    /** 完成时回填响应，供后续重放直接返回。 */
    public void complete(String scope, String idempotencyKey, int responseStatus, String responseBody) {
        jdbc.update("""
                UPDATE idempotency_records
                SET response_status = :status, response_body = :body
                WHERE scope = :scope AND idempotency_key = :key
                """,
                new MapSqlParameterSource()
                        .addValue("status", responseStatus)
                        .addValue("body", responseBody)
                        .addValue("scope", scope)
                        .addValue("key", idempotencyKey));
    }
}
