/*
 * 功能: 任务尝试仓储端口，记录每次执行结果用于排障与重试观测。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.domain;

/**
 * 任务尝试仓储端口。
 */
public interface JobAttemptRepository {

    /** 追加一条尝试记录。 */
    void insert(JobAttempt attempt);

    /**
     * 判断某次尝试是否已有记录。
     *
     * <p>用于租约恢复：上一个持有者被强制终止时不会留下 attempt 行，恢复方需为其补写；
     * 先探测可避免与 {@code (job_id, attempt_no)} 唯一约束冲突，同时不放弃该约束的门禁作用。
     *
     * @param jobId     任务业务 ID
     * @param attemptNo 尝试序号
     * @return 是否已存在
     */
    boolean exists(String jobId, int attemptNo);
}
