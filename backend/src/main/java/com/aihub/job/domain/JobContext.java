/*
 * 功能: 可靠任务执行上下文，向幂等 Handler 透传任务负载与全链路追踪信息。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.domain;

/**
 * 任务执行上下文。
 *
 * <p>Worker 领取任务后构造本上下文交给 {@link JobHandler}；Handler 必须幂等，抛出异常视为本次失败。
 *
 * @param jobId       任务业务 ID
 * @param type        任务类型
 * @param payload     任务负载（原始 JSON 字符串，已脱敏）
 * @param attempts    本次执行前的累计尝试次数
 * @param principalId 发起主体 ID
 * @param traceId     分布式追踪 ID
 * @param assetId     关联资产 ID（可空）
 */
public record JobContext(String jobId,
                         String type,
                         String payload,
                         int attempts,
                         String principalId,
                         String traceId,
                         String assetId) {
}
