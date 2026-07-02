/*
 * 功能: 任务视图 DTO，与 OpenAPI JobView 字段严格对齐。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.application;

import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobStatus;
import java.time.Instant;

/**
 * 任务视图。
 *
 * <p>字段名与 OpenAPI {@code JobView} 严格对齐：jobId、jobType、status、retryCount、nextRunAt、
 * errorCode、traceId、createdAt、updatedAt。
 *
 * @param jobId      任务业务 ID
 * @param jobType    任务类型
 * @param status     任务状态
 * @param retryCount 已尝试次数（契约字段名 retryCount）
 * @param nextRunAt  下次运行时间（可空）
 * @param errorCode  错误码（可空）
 * @param traceId    追踪 ID（可空）
 * @param createdAt  创建时间
 * @param updatedAt  更新时间
 */
public record JobView(String jobId,
                      String jobType,
                      JobStatus status,
                      int retryCount,
                      Instant nextRunAt,
                      String errorCode,
                      String traceId,
                      Instant createdAt,
                      Instant updatedAt) {

    /**
     * 从领域实体构造视图。
     */
    public static JobView from(Job job) {
        return new JobView(job.jobId(), job.type(), job.status(), job.attempts(),
                job.nextRunAt(), job.errorCode(), job.traceId(), job.createdAt(), job.updatedAt());
    }
}
