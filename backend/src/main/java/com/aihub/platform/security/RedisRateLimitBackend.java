/*
 * 功能: Redis 分布式限流后端——INCR + EXPIRE 滑动窗口。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 限流后端。
 *
 * <p>使用 1 秒突发窗口 + 60 秒持续窗口近似内存令牌桶语义。
 * Compose 默认未部署 Redis，生产多实例请显式设置 {@code aihub.rate-limit.backend=redis}。
 */
@Component
@ConditionalOnProperty(name = "aihub.rate-limit.backend", havingValue = "redis")
public class RedisRateLimitBackend implements RateLimitBackend {

    private static final int BURST_WINDOW_SECONDS = 1;
    private static final int SUSTAINED_WINDOW_SECONDS = 60;
    private static final String KEY_PREFIX = "aihub:ratelimit:";

    private final RedisRateLimitCounter counter;

    public RedisRateLimitBackend(StringRedisTemplate redisTemplate) {
        this.counter = new StringRedisCounter(redisTemplate);
    }

    RedisRateLimitBackend(RedisRateLimitCounter counter) {
        this.counter = counter;
    }

    @Override
    public boolean tryAcquire(String key, double refillPerSecond, int burst) {
        int requestsPerMinute = Math.max((int) Math.round(refillPerSecond * 60.0), 1);
        String burstKey = KEY_PREFIX + "burst:" + key;
        long burstCount = counter.increment(burstKey, BURST_WINDOW_SECONDS);
        if (burstCount > burst) {
            return false;
        }
        String windowKey = KEY_PREFIX + "window:" + key;
        long windowCount = counter.increment(windowKey, SUSTAINED_WINDOW_SECONDS);
        return windowCount <= requestsPerMinute;
    }

    private static final class StringRedisCounter implements RedisRateLimitCounter {
        private final StringRedisTemplate redisTemplate;

        private StringRedisCounter(StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        @Override
        public long increment(String key, int ttlSeconds) {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, java.time.Duration.ofSeconds(ttlSeconds));
            }
            return count == null ? Long.MAX_VALUE : count;
        }
    }
}
