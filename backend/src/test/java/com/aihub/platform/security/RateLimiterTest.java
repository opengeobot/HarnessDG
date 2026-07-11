/*
 * 功能: RateLimiter 单元测试——令牌桶补充与突发容量。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void allowsBurstThenDeniesUntilRefill() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-11T00:00:00Z"));
        AgentRateLimitProperties properties = new AgentRateLimitProperties();
        properties.setRequestsPerMinute(60);
        properties.setBurst(2);
        RateLimiter limiter = new RateLimiter(properties, new MemoryRateLimitBackend(clock));

        assertThat(limiter.tryAcquire("prn_1", "asset_search")).isTrue();
        assertThat(limiter.tryAcquire("prn_1", "asset_search")).isTrue();
        assertThat(limiter.tryAcquire("prn_1", "asset_search")).isFalse();

        clock.advance(Duration.ofSeconds(2));
        assertThat(limiter.tryAcquire("prn_1", "asset_search")).isTrue();
    }

    @Test
    void isolatesBucketsPerPrincipalAndTool() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-11T00:00:00Z"));
        AgentRateLimitProperties properties = new AgentRateLimitProperties();
        properties.setRequestsPerMinute(60);
        properties.setBurst(1);
        RateLimiter limiter = new RateLimiter(properties, new MemoryRateLimitBackend(clock));

        assertThat(limiter.tryAcquire("prn_1", "asset_search")).isTrue();
        assertThat(limiter.tryAcquire("prn_2", "asset_search")).isTrue();
        assertThat(limiter.tryAcquire("prn_1", "asset_get")).isTrue();
        assertThat(limiter.tryAcquire("prn_1", "asset_search")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
