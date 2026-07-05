/*
 * 功能: 幂等辅助工具——为 Controller 写端点提供幂等键构建、摘要与序列化能力。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.shared.idempotency;

import com.aihub.job.application.IdempotencyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 幂等 Controller 辅助。
 *
 * <p>封装 {@code IdempotencyKey} 构建、请求体摘要与响应序列化/反序列化，
 * 供写端点 Controller 复用，避免每个 Controller 重复样板代码。
 */
public final class IdempotencySupport {

    private final ObjectMapper objectMapper;

    public IdempotencySupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 Idempotency-Key header 值构建幂等键；值为空时返回 null（跳过幂等）。
     */
    public IdempotencyKey buildKey(String headerValue, String principalId,
                                   String method, String path) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        return new IdempotencyKey(headerValue, principalId, method, path);
    }

    /**
     * 计算请求体 SHA-256 摘要；失败时返回 "unknown"。
     */
    public String sha256Digest(Object body) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    /**
     * 将对象序列化为 JSON 字符串。
     */
    public String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize idempotency response", e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型。
     */
    public <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize idempotency response", e);
        }
    }

    /**
     * 便捷方法：包装 supplier 为幂等响应并执行。
     */
    public IdempotencyService.IdempotencyResult executeWrite(
            IdempotencyService idempotencyService,
            IdempotencyKey key,
            String fingerprint,
            int successStatus,
            Object resultBody) {
        return idempotencyService.execute(key, fingerprint, () ->
                new IdempotencyService.IdempotencyResponse(successStatus, serialize(resultBody)));
    }
}
