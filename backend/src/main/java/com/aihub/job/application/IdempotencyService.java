/*
 * 功能: 幂等应用服务——解析 Idempotency-Key 头，命中复用首次结果，未命中执行并落库。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.job.application;

import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencyRecord;
import com.aihub.shared.idempotency.IdempotencyStore;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * 幂等应用服务。
 *
 * <p>供写接口控制器/服务调用：解析 {@code Idempotency-Key} 头，命中时复用首次结果，未命中时执行业务逻辑
 * 并落库首次结果。重复提交同一 key 返回首次结果，保证"恰好一次"语义。
 *
 * <p>P0-B 采用显式调用（非 AOP 全局拦截），避免过度工程；控制器在需要时调用本服务。
 */
@Service
public class IdempotencyService {

    private final IdempotencyStore idempotencyStore;

    public IdempotencyService(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    /**
     * 幂等执行：命中复用首次结果，未命中执行 supplier 并落库。
     *
     * @param key             幂等键（可空，为空时直接执行不幂等）
     * @param requestFingerprint 请求体指纹（用于检测同键不同请求体冲突）
     * @param supplier        业务逻辑供应者（仅首次执行）
     * @return 首次执行结果
     */
    public IdempotencyResult execute(IdempotencyKey key, String requestFingerprint,
                                     Supplier<IdempotencyResponse> supplier) {
        if (key == null) {
            IdempotencyResponse response = supplier.get();
            return new IdempotencyResult(response, false);
        }
        Optional<IdempotencyRecord> existing = idempotencyStore.find(key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (requestFingerprint != null && record.requestFingerprint() != null
                    && !requestFingerprint.equals(record.requestFingerprint())) {
                throw new ConflictException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                        "same idempotency key with different request body",
                        Map.of("key", key.key()));
            }
            return new IdempotencyResult(
                    new IdempotencyResponse(record.responseStatus(), record.responseBody()), true);
        }
        IdempotencyResponse response = supplier.get();
        IdempotencyRecord record = new IdempotencyRecord(
                key, requestFingerprint, response.status(), response.body(), java.time.Instant.now());
        idempotencyStore.save(record);
        return new IdempotencyResult(response, false);
    }

    /**
     * 幂等执行结果。
     *
     * @param response  响应（首次或命中）
     * @param replayed  是否为命中复用（true 表示命中已存在记录，未实际执行）
     */
    public record IdempotencyResult(IdempotencyResponse response, boolean replayed) {
    }

    /**
     * 幂等响应。
     *
     * @param status HTTP 状态码
     * @param body   响应体（已脱敏）
     */
    public record IdempotencyResponse(int status, String body) {
    }
}
