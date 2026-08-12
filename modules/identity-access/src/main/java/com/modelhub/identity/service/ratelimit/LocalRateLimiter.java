package com.modelhub.identity.service.ratelimit;

import com.modelhub.identity.config.IdentityProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地保守限流（02 §6.2 降级模式）：Redis 不可用或测试场景使用；
 * 固定窗口计数，窗口滚动时清零。
 */
public class LocalRateLimiter implements RateLimiter {

    private record Bucket(AtomicLong count, long windowStart) {}

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LocalRateLimiter(IdentityProperties props) {
        // 降级模式使用更保守（更小）的阈值
        this.maxAttempts = Math.max(1, props.rateLimit().maxAttempts() / 2);
        this.windowMillis = props.rateLimit().windowSeconds() * 1000L;
    }

    @Override
    public Verdict tryAcquire(String dimension) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.compute(dimension, (k, b) ->
                (b == null || now - b.windowStart() >= windowMillis) ? new Bucket(new AtomicLong(0), now) : b);
        long n = bucket.count().incrementAndGet();
        buckets.keySet().removeIf(k -> now - safeWindowStart(k) > windowMillis * 10);
        if (n > maxAttempts) {
            long elapsed = now - bucket.windowStart();
            return Verdict.deny((windowMillis - elapsed) / 1000 + 1);
        }
        return Verdict.allow();
    }

    private long safeWindowStart(String key) {
        Bucket b = buckets.get(key);
        return b == null ? 0 : b.windowStart();
    }
}
