/*
 * 功能: 任务领取结果，携带领取前的状态用于识别「被强杀后按租约重新领取」。
 * 时间: 2026-09-20
 * 作者: AxeXie
 */
package com.aihub.job.domain;

import java.time.Instant;

/**
 * 任务领取结果。
 *
 * <p>{@link JobRepository#claimNext} 的返回值。除领取到的 {@link Job} 外，还携带该行在领取
 * <b>之前</b>的状态与尝试次数：若领取前状态为 {@link JobStatus#RUNNING}，说明上一个持有者
 * 未正常结束（进程被强制终止或租约超时），本次属于租约恢复而非首次执行。Worker 据此为被
 * 终止的那次尝试补写记录，使「领取后被终止」在数据上可观测（{@code INV-JOB-005}）。
 *
 * @param job                 领取后的任务（{@code status=RUNNING}，{@code attempts} 已计入本次尝试）
 * @param previousStatus      领取前的任务状态
 * @param previousAttempts    领取前的累计尝试次数
 * @param previousUpdatedAt   领取前该行的更新时间；租约恢复时即被终止尝试的开始时刻
 *                            （{@code job_task.updated_at} 在领取时被写入）。{@code started_at}
 *                            在 {@code job_attempt} 中为 NOT NULL，故必须以真实时间补齐记录。
 */
public record JobClaim(Job job, JobStatus previousStatus, int previousAttempts,
                       Instant previousUpdatedAt) {

    /**
     * 本次领取是否属于「上一个持有者未正常结束」的租约恢复。
     *
     * @return 领取前是否为 RUNNING
     */
    public boolean recoveredFromAbandonedLease() {
        return previousStatus == JobStatus.RUNNING;
    }
}
