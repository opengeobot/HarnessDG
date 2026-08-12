package com.modelhub.identity.service.ratelimit;

import com.modelhub.identity.config.IdentityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * Redis 固定窗口限流（用户+IP 双维度，02 §6.2）：Lua 原子自增 + 首次设置 TTL。
 * Redis 异常由 {@link ResilientRateLimiter} 捕获并降级到本地保守模式。
 */
public class RedisRateLimiter implements RateLimiter {

    private static final DefaultRedisScript<Long> INCR_WINDOW_SCRIPT;

    static {
        INCR_WINDOW_SCRIPT = new DefaultRedisScript<>();
        INCR_WINDOW_SCRIPT.setResultType(Long.class);
        INCR_WINDOW_SCRIPT.setScriptText("""
                local n = redis.call('INCR', KEYS[1])
                if n == 1 then
                  redis.call('EXPIRE', KEYS[1], ARGV[2])
                end
                return n
                """);
    }

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final int windowSeconds;

    public RedisRateLimiter(StringRedisTemplate redis, IdentityProperties props) {
        this.redis = redis;
        this.maxAttempts = props.rateLimit().maxAttempts();
        this.windowSeconds = props.rateLimit().windowSeconds();
    }

    @Override
    public Verdict tryAcquire(String dimension) {
        long windowStart = System.currentTimeMillis() / 1000 / windowSeconds * windowSeconds;
        String key = "rl:" + dimension + ":" + windowStart;
        Long n = redis.execute(INCR_WINDOW_SCRIPT, List.of(key), String.valueOf(maxAttempts),
                String.valueOf(windowSeconds));
        if (n == null) {
            throw new IllegalStateException("redis lua returned null");
        }
        if (n > maxAttempts) {
            long elapsed = System.currentTimeMillis() / 1000 - windowStart;
            return Verdict.deny(windowSeconds - elapsed);
        }
        return Verdict.allow();
    }
}
