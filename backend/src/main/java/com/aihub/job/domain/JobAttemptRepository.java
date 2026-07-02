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
}
