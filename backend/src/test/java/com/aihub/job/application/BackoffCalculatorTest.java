/*
 * 功能: 退避计算器单元测试——验证指数退避+抖动区间与上限。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.job.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 退避计算器单元测试。
 */
class BackoffCalculatorTest {

    @Test
    void shouldComputeExponentialBackoffWithinBounds() {
        BackoffCalculator backoff = new BackoffCalculator(1000L, 60_000L);
        Instant now = Instant.now();

        // 第1次失败：base=1000, 抖动在 [500, 1000]
        Instant first = backoff.nextRunAt(1, now);
        long firstDelay = first.toEpochMilli() - now.toEpochMilli();
        assertThat(firstDelay).isBetween(500L, 1000L);

        // 第2次失败：base*2=2000, 抖动在 [1000, 2000]
        Instant second = backoff.nextRunAt(2, now);
        long secondDelay = second.toEpochMilli() - now.toEpochMilli();
        assertThat(secondDelay).isBetween(1000L, 2000L);

        // 第3次失败：base*4=4000, 抖动在 [2000, 4000]
        Instant third = backoff.nextRunAt(3, now);
        long thirdDelay = third.toEpochMilli() - now.toEpochMilli();
        assertThat(thirdDelay).isBetween(2000L, 4000L);
    }

    @Test
    void shouldCapAtMaximum() {
        BackoffCalculator backoff = new BackoffCalculator(1000L, 5000L);
        Instant now = Instant.now();

        // 第10次失败：base*2^9=512000，应被限制在 cap=5000
        Instant tenth = backoff.nextRunAt(10, now);
        long delay = tenth.toEpochMilli() - now.toEpochMilli();
        assertThat(delay).isBetween(2500L, 5000L);
    }

    @Test
    void shouldAlwaysBeAfterNow() {
        BackoffCalculator backoff = new BackoffCalculator(100L, 1000L);
        Instant now = Instant.now();
        for (int i = 1; i <= 10; i++) {
            Instant next = backoff.nextRunAt(i, now);
            assertThat(next).isAfterOrEqualTo(now);
        }
    }
}
