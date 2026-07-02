/*
 * 功能: 可靠任务领域实体，承载任务状态机字段与全链路追踪上下文。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.domain;

import java.time.Instant;

/**
 * 可靠任务领域实体。
 *
 * <p>持久化于 {@code job_task} 表；由 Worker 以 FOR UPDATE SKIP LOCKED 领取并驱动状态机。
 * 全链路透传 traceId/principalId/assetId。payload 以原始 JSON 字符串承载，避免领域依赖序列化框架。
 *
 * @param id           内部主键
 * @param jobId        任务业务 ID（job_）
 * @param type         任务类型，路由到对应幂等 JobHandler
 * @param payload      任务负载（原始 JSON 字符串，已脱敏）
 * @param status       任务状态
 * @param maxAttempts  最大尝试次数
 * @param attempts     已尝试次数
 * @param nextRunAt    下次可领取时间
 * @param leasedUntil  租约到期时间
 * @param leasedBy     领取者标识
 * @param traceId      分布式追踪 ID
 * @param principalId  发起主体 ID
 * @param assetId      关联资产 ID
 * @param errorCode    最近一次失败错误码
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @param rowVersion   乐观锁版本号
 */
public record Job(Long id,
                  String jobId,
                  String type,
                  String payload,
                  JobStatus status,
                  int maxAttempts,
                  int attempts,
                  Instant nextRunAt,
                  Instant leasedUntil,
                  String leasedBy,
                  String traceId,
                  String principalId,
                  String assetId,
                  String errorCode,
                  Instant createdAt,
                  Instant updatedAt,
                  int rowVersion) {
}
