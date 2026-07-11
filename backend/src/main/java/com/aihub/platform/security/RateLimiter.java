/*
 * 功能: 按主体 + 工具名的令牌桶限流器，供 Agent REST 与 MCP tools/call 复用。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 主体级 + 工具级令牌桶限流器。
 *
 * <p>每个 {@code principalId:toolName} 维护独立桶；补充速率由
 * {@link AgentRateLimitProperties#getRequestsPerMinute()} 决定，突发容量由 {@link AgentRateLimitProperties#getBurst()} 决定。
 */
@Component
public class RateLimiter {

    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final double refillPerSecond;
    private final int burst;
    private final Clock clock;

    public RateLimiter(AgentRateLimitProperties properties, Clock clock) {
        this.refillPerSecond = Math.max(properties.getRequestsPerMinute(), 1) / 60.0;
        this.burst = Math.max(properties.getBurst(), 1);
        this.clock = clock;
    }

    /**
     * 尝试获取一次调用配额。
     *
     * @param principalId 主体 ID
     * @param toolName    MCP 工具名或 REST 等价工具名
     * @return 是否允许本次调用
     */
    public boolean tryAcquire(String principalId, String toolName) {
        if (principalId == null || principalId.isBlank() || toolName == null || toolName.isBlank()) {
            return true;
        }
        String key = principalId + ":" + toolName;
        TokenBucket bucket = buckets.computeIfAbsent(key, ignored -> new TokenBucket(burst, clock));
        return bucket.tryConsume(1, refillPerSecond);
    }

    private static final class TokenBucket {
        private final int capacity;
        private final Clock clock;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(int capacity, Clock clock) {
            this.capacity = capacity;
            this.clock = clock;
            this.tokens = capacity;
            this.lastRefillNanos = clock.instant().getNano() + clock.instant().getEpochSecond() * 1_000_000_000L;
        }

        synchronized boolean tryConsume(double amount, double refillPerSecond) {
            refill(refillPerSecond);
            if (tokens < amount) {
                return false;
            }
            tokens -= amount;
            return true;
        }

        private void refill(double refillPerSecond) {
            long nowNanos = clock.instant().getNano() + clock.instant().getEpochSecond() * 1_000_000_000L;
            double elapsedSeconds = (nowNanos - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
                lastRefillNanos = nowNanos;
            }
        }
    }
}
