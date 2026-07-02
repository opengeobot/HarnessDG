/*
 * 功能: 可靠任务仓储端口，定义任务状态机持久化与领取能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 可靠任务仓储端口。
 *
 * <p>领取采用 {@code FOR UPDATE SKIP LOCKED}，保证多 Worker 并发不重复领取；
 * 租约到期（{@code leased_until < now} 且仍 RUNNING）可重新领取（租约恢复）。
 * 领域端口不感知 SQL/MyBatis，由基础设施层提供 JDBC 实现。
 */
public interface JobRepository {

    /** 创建任务。 */
    void insert(Job job);

    /** 按业务 ID 查找任务。 */
    Optional<Job> findByJobId(String jobId);

    /**
     * 游标分页查询任务列表（按 created_at, id 键集）。
     *
     * @param status     状态过滤（可空，表示全部）
     * @param cursorTime 游标创建时间（可空，从开头查）
     * @param cursorId   游标内部 ID（可空）
     * @param limit       每页条数
     * @return 该页任务（已按 created_at, id 升序）
     */
    List<Job> list(String status, Instant cursorTime, Long cursorId, int limit);

    /**
     * 领取一个待执行任务（PENDING/RETRY_WAIT 且 next_run_at<=now，或租约到期的 RUNNING）。
     * 以 FOR UPDATE SKIP LOCKED 加锁并置为 RUNNING，设置租约与领取者。
     *
     * @param workerId    Worker 实例标识
     * @param now         当前时间
     * @param leaseSeconds 租约时长（秒）
     * @return 领取到的任务，无任务时为空
     */
    Optional<Job> claimNext(String workerId, Instant now, long leaseSeconds);

    /** 标记任务成功（SUCCEEDED）。 */
    void markSucceeded(String jobId, Instant now);

    /** 标记任务进入退避等待（RETRY_WAIT），更新 attempts 与 next_run_at。 */
    void markRetryWait(String jobId, int attempts, Instant nextRunAt, Instant now);

    /** 标记任务放弃（DEAD），记录错误码。 */
    void markDead(String jobId, String errorCode, Instant now);

    /** 人工重试：将 DEAD/RETRY_WAIT 任务重置为 PENDING。返回是否实际重置。 */
    boolean resetToPending(String jobId, Instant now);

    /** 人工取消：仅 PENDING/RETRY_WAIT 可取消。返回是否实际取消。 */
    boolean cancel(String jobId, Instant now);

    /** 统计某状态任务数（用于指标）。 */
    long countByStatus(String status);
}
