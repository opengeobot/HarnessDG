package com.modelhub.identity.service.ratelimit;

/**
 * 限流结果：allowed=false 时必须返回 429 RATE_LIMITED 与 Retry-After（04 §2.2）。
 */
public interface RateLimiter {

    record Verdict(boolean allowed, long retryAfterSeconds) {
        public static Verdict allow() {
            return new Verdict(true, 0);
        }

        public static Verdict deny(long retryAfterSeconds) {
            return new Verdict(false, Math.max(1, retryAfterSeconds));
        }
    }

    /** dimension 形如 login:user:<username> / login:ip:<ip>；固定窗口计数。 */
    Verdict tryAcquire(String dimension);
}
