/*
 * 功能: 幂等记录只读摘要——领域查询投影，非持久化实体。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.job.domain;

import java.time.Instant;

/**
 * 幂等记录只读摘要。
 */
public record IdempotencyRecordSummary(String idempotencyKey,
                                       String principalId,
                                       String method,
                                       String path,
                                       String requestDigest,
                                       Instant createdAt) {
}
