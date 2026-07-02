/*
 * 功能: 任务尝试记录，每次执行落一行用于排障与重试观测。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.domain;

import java.time.Instant;

/**
 * 任务尝试记录。
 *
 * @param id           内部主键
 * @param jobId        关联任务业务 ID
 * @param attemptNo    尝试序号（从 1 递增）
 * @param startedAt    开始时间
 * @param endedAt      结束时间
 * @param status       尝试结果：SUCCESS/FAILED/DEAD
 * @param errorMessage 失败错误信息（已脱敏）
 * @param errorCode    失败错误码
 * @param durationMs   耗时（毫秒）
 */
public record JobAttempt(Long id,
                         String jobId,
                         int attemptNo,
                         Instant startedAt,
                         Instant endedAt,
                         String status,
                         String errorMessage,
                         String errorCode,
                         Long durationMs) {
}
