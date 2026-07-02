/*
 * 功能: 指数退避+抖动计算器，为可靠任务重试计算下次运行时间。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.application;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 指数退避+抖动计算器。
 *
 * <p>重试间隔 = min(base * 2^(attempt-1), cap) ± 抖动，避免惊群与持续重试风暴。
 */
public final class BackoffCalculator {

    private final long baseMillis;
    private final long capMillis;

    public BackoffCalculator(long baseMillis, long capMillis) {
        this.baseMillis = baseMillis;
        this.capMillis = capMillis;
    }

    /**
     * 计算第 {@code attempt} 次失败后的下次运行时间。
     *
     * @param attempt    本次失败的累计尝试次数（>=1）
     * @param now        当前时间
     * @return 下次运行时间
     */
    public Instant nextRunAt(int attempt, Instant now) {
        long exp = Math.min(baseMillis * (1L << Math.max(0, attempt - 1)), capMillis);
        // 全额抖动（full jitter）：在 [exp/2, exp] 区间取值。
        long jitter = ThreadLocalRandom.current().nextLong(exp / 2 + 1, exp + 1);
        return now.plusMillis(jitter);
    }
}
