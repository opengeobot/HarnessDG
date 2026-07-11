/*
 * 功能: RedisRateLimitBackend 单元测试——内存计数器验证滑动窗口逻辑。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedisRateLimitBackendTest {

    @Test
    void enforcesBurstAndSustainedWindows() {
        InMemoryCounter counter = new InMemoryCounter();
        RedisRateLimitBackend backend = new RedisRateLimitBackend(counter);
        double refillPerSecond = 1.0;
        int burst = 2;

        assertThat(backend.tryAcquire("prn_1:asset_search", refillPerSecond, burst)).isTrue();
        assertThat(backend.tryAcquire("prn_1:asset_search", refillPerSecond, burst)).isTrue();
        assertThat(backend.tryAcquire("prn_1:asset_search", refillPerSecond, burst)).isFalse();
    }

    @Test
    void isolatesKeys() {
        InMemoryCounter counter = new InMemoryCounter();
        RedisRateLimitBackend backend = new RedisRateLimitBackend(counter);

        assertThat(backend.tryAcquire("prn_1:asset_search", 1.0, 1)).isTrue();
        assertThat(backend.tryAcquire("prn_2:asset_search", 1.0, 1)).isTrue();
        assertThat(backend.tryAcquire("prn_1:asset_search", 1.0, 1)).isFalse();
    }

    private static final class InMemoryCounter implements RedisRateLimitCounter {
        private final Map<String, Long> counts = new HashMap<>();

        @Override
        public long increment(String key, int ttlSeconds) {
            return counts.merge(key, 1L, Long::sum);
        }
    }
}
