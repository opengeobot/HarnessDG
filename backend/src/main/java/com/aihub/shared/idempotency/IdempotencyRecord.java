/*
 * 功能: 幂等记录 DTO，承载首次执行结果以供命中复用。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.idempotency;

import java.time.Instant;

/**
 * 幂等记录。
 *
 * <p>记录首次执行的请求指纹、响应状态与已脱敏的结果摘要；命中时直接复用，禁止重复执行副作用。
 * {@code requestFingerprint} 用于检测同键不同请求体的冲突。
 *
 * @param key                 幂等键
 * @param requestFingerprint  请求体指纹（如规范化哈希）
 * @param responseStatus      首次响应 HTTP 状态
 * @param responseBody        首次响应体（已脱敏）
 * @param createdAt           首次记录时间
 */
public record IdempotencyRecord(IdempotencyKey key,
                                String requestFingerprint,
                                int responseStatus,
                                String responseBody,
                                Instant createdAt) {
}
