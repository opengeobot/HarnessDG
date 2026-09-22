/*
 * 功能: 进程内令牌桶限流后端（默认单实例）。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 内存令牌桶限流后端。
 *
 * <p>默认后端（{@code aihub.rate-limit.backend=memory}），适用于单进程与开发环境。
 * 使用 Caffeine {@code expireAfterAccess(10min)} 自动淘汰不活跃桶，防止长期运行内存泄漏。
 */
@Component
@ConditionalOnProperty(name = "aihub.rate-limit.backend", havingValue = "memory", matchIfMissing = true)
public class MemoryRateLimitBackend implements RateLimitBackend {

    private final Cache<String, TokenBucket> buckets;
    private final Clock clock;

    public MemoryRateLimitBackend(Clock clock) {
        this.clock = clock;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    @Override
    public boolean tryAcquire(String key, double refillPerSecond, int burst) {
        TokenBucket bucket = buckets.get(key, ignored -> new TokenBucket(burst, clock));
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
            this.lastRefillNanos = toNanos(clock);
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
            long nowNanos = toNanos(clock);
            double elapsedSeconds = (nowNanos - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
                lastRefillNanos = nowNanos;
            }
        }

        private static long toNanos(Clock clock) {
            return clock.instant().getNano() + clock.instant().getEpochSecond() * 1_000_000_000L;
        }
    }
}
