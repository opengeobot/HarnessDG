package com.modelhub.identity.service.ratelimit;

import com.modelhub.shared.metrics.BusinessCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 优先 Redis，失败自动降级到本地保守限流（02 §6.2）。
 * Redis 是加速与共享计数层，不是限流可用性前提。
 * 拒绝判决计入限流触发指标（07 §5.2）。
 */
public class ResilientRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ResilientRateLimiter.class);

    private final RateLimiter redis;
    private final RateLimiter local;
    private final BusinessCounters counters;

    public ResilientRateLimiter(RateLimiter redis, RateLimiter local, BusinessCounters counters) {
        this.redis = redis;
        this.local = local;
        this.counters = counters;
    }

    @Override
    public Verdict tryAcquire(String dimension) {
        Verdict verdict;
        try {
            verdict = redis.tryAcquire(dimension);
        } catch (RuntimeException e) {
            log.warn("Redis 限流不可用，降级本地保守模式: {}", e.getMessage());
            verdict = local.tryAcquire(dimension);
        }
        if (!verdict.allowed()) {
            counters.rateLimitTrigger();
        }
        return verdict;
    }
}
