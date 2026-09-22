/*
 * 功能: 可靠任务 Worker——领取、执行、指数退避重试、DEAD 通知，全链路透传追踪上下文。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.job.application.BackoffCalculator;
import com.aihub.job.application.JobAuditPort;
import com.aihub.job.application.JobNotificationPort;
import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobAttempt;
import com.aihub.job.domain.JobAttemptRepository;
import com.aihub.job.domain.JobClaim;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.job.domain.JobRepository;
import com.aihub.job.domain.JobAttemptRepository;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.error.PlatformException;
import com.aihub.shared.logging.SensitiveDataMasker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 可靠任务 Worker。
 *
 * <p>每次 {@link #tick()} 领取一个待执行任务（FOR UPDATE SKIP LOCKED），调用幂等 Handler；
 * 成功置 SUCCEEDED；失败时按 {@link com.aihub.shared.error.ErrorCode#retryable()} 区分：可重试错误
 * 按指数退避+抖动置 RETRY_WAIT，不可重试错误或已达 max_attempts 置 DEAD 并触发 JOB_DEAD 通知。
 * 全链路透传 traceId/principalId/assetId。
 *
 * <p><b>异常边界</b>：{@link #tick()} 捕获自身全部异常（含领取与落库失败）。调度器以
 * {@code scheduleAtFixedRate} 驱动本方法，若异常逸出，{@code ScheduledThreadPoolExecutor} 会静默
 * 取消该周期任务且无 error handler，Worker 将永久停摆；因此这里绝不允许异常逸出。
 *
 * <p><b>租约恢复</b>：若领取前该行处于 RUNNING，说明上一个持有者未正常结束（进程被强制终止或
 * 租约超时），本次为租约恢复。此时为被终止的那次尝试补写 attempt 记录（错误码
 * {@code JOB_LEASE_EXPIRED}），使该事实可观测。
 *
 * <p>本类不作为组件被扫描，由 {@link JobConfiguration} 显式装配。
 */
public class JobWorker {

    private static final Logger LOG = LoggerFactory.getLogger(JobWorker.class);

    /** 被强制终止的尝试所使用的错误码（不新增 ErrorCode 枚举项）。 */
    static final String LEASE_EXPIRED_ERROR_CODE = "JOB_LEASE_EXPIRED";
    /** 未注册 Handler 时的错误码。 */
    static final String HANDLER_NOT_FOUND_ERROR_CODE = "JOB_HANDLER_NOT_FOUND";
    /** 无法归类异常时使用的错误码。 */
    static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";

    private final JobRepository jobRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final JobHandlerRegistry handlerRegistry;
    private final BackoffCalculator backoff;
    private final ObjectProvider<JobNotificationPort> notificationPortProvider;
    private final ObjectProvider<JobAuditPort> auditPortProvider;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final SensitiveDataMasker masker;
    private final long leaseSeconds;
    private final String workerId;
    private final Counter jobsClaimed;
    private final Counter jobsSucceeded;
    private final Counter jobsFailed;
    private final Counter jobsDead;
    private final PlatformMetrics platformMetrics;

    public JobWorker(JobRepository jobRepository,
                     JobAttemptRepository jobAttemptRepository,
                     JobHandlerRegistry handlerRegistry,
                     BackoffCalculator backoff,
                     ObjectProvider<JobNotificationPort> notificationPortProvider,
                     ObjectProvider<JobAuditPort> auditPortProvider,
                     TransactionTemplate transactionTemplate,
                     Clock clock,
                     ObjectProvider<MeterRegistry> meterRegistryProvider,
                     SensitiveDataMasker masker,
                     long leaseSeconds,
                     ObjectProvider<PlatformMetrics> platformMetricsProvider) {
        this.jobRepository = jobRepository;
        this.jobAttemptRepository = jobAttemptRepository;
        this.handlerRegistry = handlerRegistry;
        this.backoff = backoff;
        this.notificationPortProvider = notificationPortProvider;
        this.auditPortProvider = auditPortProvider;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.masker = masker;
        this.leaseSeconds = leaseSeconds;
        this.workerId = "worker-" + Integer.toHexString((int) ProcessHandle.current().pid());
        this.platformMetrics = platformMetricsProvider.getIfAvailable();
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            this.jobsClaimed = registry.counter("job.processed", "result", "claimed");
            this.jobsSucceeded = registry.counter("job.processed", "result", "succeeded");
            this.jobsFailed = registry.counter("job.processed", "result", "failed");
            this.jobsDead = registry.counter("job.processed", "result", "dead");
        } else {
            this.jobsClaimed = new NoopCounter();
            this.jobsSucceeded = new NoopCounter();
            this.jobsFailed = new NoopCounter();
            this.jobsDead = new NoopCounter();
        }
    }

    /**
     * 领取并执行一个任务。无任务时直接返回。本方法不向外抛出异常，保证调度周期不被取消。
     */
    public void tick() {
        try {
            executeOnce();
        } catch (Exception ex) {
            // 领取或落库失败（如数据库瞬时不可用）不得取消调度任务；下一 tick 自愈重试。
            LOG.error("job worker tick failed; scheduling continues", ex);
        }
    }

    private void executeOnce() {
        Instant now = clock.instant();
        Optional<JobClaim> claimed = jobRepository.claimNext(workerId, now, leaseSeconds);
        if (claimed.isEmpty()) {
            return;
        }
        JobClaim claim = claimed.get();
        Job job = claim.job();
        jobsClaimed.increment();

        // attempts 已在领取时递增，故当前尝试序号即 jobs.attempts()。
        int attemptNo = job.attempts();
        Instant startedAt = now;
        JobContext context = new JobContext(job.jobId(), job.type(), job.payload(), job.attempts(),
                job.principalId(), job.traceId(), job.assetId());

        if (claim.recoveredFromAbandonedLease()) {
            recordAbandonedAttempt(job, claim.previousUpdatedAt());
        }

        Optional<JobHandler> handler = handlerRegistry.resolve(job.type());
        if (handler.isEmpty()) {
            handleNoHandler(job, attemptNo, startedAt);
            return;
        }

        try {
            handler.get().handle(context);
            Instant endedAt = clock.instant();
            jobRepository.markSucceeded(job.jobId(), endedAt);
            recordAttempt(new JobAttempt(null, job.jobId(), attemptNo, startedAt, endedAt,
                    "SUCCESS", null, null, Duration.between(startedAt, endedAt).toMillis(),
                    job.traceId()));
            jobsSucceeded.increment();
            if (platformMetrics != null) {
                platformMetrics.recordJobProcessingDuration(
                        job.type(), Duration.between(startedAt, endedAt));
            }
            LOG.info("job succeeded jobId={} type={} attempt={}", job.jobId(), job.type(), attemptNo);
        } catch (Exception ex) {
            handleFailure(job, attemptNo, startedAt, ex);
        }
    }

    /**
     * 为被强制终止的上一次尝试补写记录。
     *
     * <p>被终止的进程不会留下 attempt 行；恢复方以 {@link #LEASE_EXPIRED_ERROR_CODE} 明确标注，
     * 使「领取后被终止」可查询。{@code abandonedAt} 取领取前该行的 {@code updated_at}
     * （即上一次领取的时刻），因为 {@code job_attempt.started_at} 是 NOT NULL，
     * 无法用 null 表达「未知开始时间」。若该序号已有记录
     * （例如持有者已写入失败行但未及更新状态），则保留既有记录，不覆盖、不冲突。
     */
    private void recordAbandonedAttempt(Job job, Instant abandonedAt) {
        int abandonedAttemptNo = job.attempts() - 1;
        if (abandonedAttemptNo < 1) {
            return;
        }
        recordAttempt(new JobAttempt(null, job.jobId(), abandonedAttemptNo,
                abandonedAt == null ? job.updatedAt() : abandonedAt, job.updatedAt(),
                "FAILED", "previous holder was terminated before completing the attempt",
                LEASE_EXPIRED_ERROR_CODE, null, job.traceId()));
        LOG.warn("job lease expired; abandoned attempt recorded jobId={} attempt={}",
                job.jobId(), abandonedAttemptNo);
    }

    private void handleNoHandler(Job job, int attemptNo, Instant startedAt) {
        Instant endedAt = clock.instant();
        // 状态迁移、attempt 与 JOB_DEAD 审计同事务原子提交（一致性门禁：
        // 迁移成功而无审计、或审计而无迁移均为缺陷）；通知为旁路，置于事务之外。
        transactionTemplate.executeWithoutResult(status -> {
            jobRepository.markDead(job.jobId(), HANDLER_NOT_FOUND_ERROR_CODE, endedAt);
            recordAttempt(new JobAttempt(null, job.jobId(), attemptNo, startedAt, endedAt,
                    "DEAD", "no handler registered for type " + job.type(), HANDLER_NOT_FOUND_ERROR_CODE,
                    Duration.between(startedAt, endedAt).toMillis(), job.traceId()));
            auditJobDead(job, attemptNo, HANDLER_NOT_FOUND_ERROR_CODE);
        });
        jobsDead.increment();
        notifyJobDead(job, attemptNo, HANDLER_NOT_FOUND_ERROR_CODE);
        LOG.warn("job dead (no handler) jobId={} type={}", job.jobId(), job.type());
    }

    private void handleFailure(Job job, int attemptNo, Instant startedAt, Exception ex) {
        Instant endedAt = clock.instant();
        long durationMs = Duration.between(startedAt, endedAt).toMillis();
        String errorCode = resolveErrorCode(ex);
        String message = maskMessage(ex.getMessage());

        if (isTerminalFailure(ex, attemptNo, job.maxAttempts())) {
            // 与 handleNoHandler 同理：迁移、attempt、审计同事务；通知旁路在事务外。
            transactionTemplate.executeWithoutResult(status -> {
                jobRepository.markDead(job.jobId(), errorCode, endedAt);
                recordAttempt(new JobAttempt(null, job.jobId(), attemptNo, startedAt, endedAt,
                        "DEAD", message, errorCode, durationMs, job.traceId()));
                auditJobDead(job, attemptNo, errorCode);
            });
            jobsDead.increment();
            notifyJobDead(job, attemptNo, errorCode);
            LOG.warn("job dead jobId={} type={} attempts={} error={}",
                    job.jobId(), job.type(), attemptNo, errorCode);
        } else {
            Instant nextRunAt = backoff.nextRunAt(attemptNo, endedAt);
            jobRepository.markRetryWait(job.jobId(), nextRunAt, endedAt);
            recordAttempt(new JobAttempt(null, job.jobId(), attemptNo, startedAt, endedAt,
                    "FAILED", message, errorCode, durationMs, job.traceId()));
            jobsFailed.increment();
            LOG.info("job retry_wait jobId={} type={} attempts={} nextRunAt={} error={}",
                    job.jobId(), job.type(), attemptNo, nextRunAt, errorCode);
        }
    }

    /**
     * 判定本次失败是否为终态（DEAD）。
     *
     * <p>不可重试错误立即终止，不再消耗剩余重试预算（{@code INV-JOB-006}）；可重试错误在达到
     * max_attempts 时终止。非 {@link PlatformException} 的异常无法归类，按可重试处理，
     * 以免把瞬时故障误判为终态。
     */
    private static boolean isTerminalFailure(Exception ex, int attemptNo, int maxAttempts) {
        if (ex instanceof PlatformException pe && !pe.errorCode().retryable()) {
            return true;
        }
        return attemptNo >= maxAttempts;
    }

    private static String resolveErrorCode(Exception ex) {
        return ex instanceof PlatformException pe ? pe.errorCode().name() : INTERNAL_ERROR_CODE;
    }

    /**
     * 写入一条 attempt 记录；若该序号已有记录则跳过。
     *
     * <p>唯一约束 {@code uq_job_attempt_job_no} 是真正的门禁；此处的探测避免把「人工重试后
     * 复用已耗尽的尝试序号」这类可预期情形升级为异常（该语义由 TASK-P0BR-024 明确定义）。
     */
    private void recordAttempt(JobAttempt attempt) {
        if (attempt.attemptNo() >= 1 && jobAttemptRepository.exists(attempt.jobId(), attempt.attemptNo())) {
            LOG.warn("attempt record already exists; skipped jobId={} attempt={}",
                    attempt.jobId(), attempt.attemptNo());
            return;
        }
        jobAttemptRepository.insert(attempt);
    }

    private void notifyJobDead(Job job, int attemptNo, String errorCode) {
        JobNotificationPort port = notificationPortProvider.getIfAvailable();
        if (port != null) {
            port.notifyJobDead(job.jobId(), job.type(), attemptNo, errorCode, job.principalId());
        }
    }

    /**
     * 写 JOB_DEAD 审计（REQ-JOB-001 声明的事件；契约口径 Worker 侧事件，无 REST 操作）。
     *
     * <p>摘要只含 jobId/type/attempts/errorCode——payload 原文与凭据不得进入审计。
     * actor 传任务归属主体（principalId），无归属时由适配器落 system。
     */
    private void auditJobDead(Job job, int attemptNo, String errorCode) {
        JobAuditPort port = auditPortProvider.getIfAvailable();
        if (port == null) {
            return;
        }
        port.record("JOB_DEAD", null, job.jobId(), job.principalId(), Map.of(
                "jobType", job.type(),
                "attempts", attemptNo,
                "errorCode", errorCode == null ? "UNKNOWN" : errorCode));
    }

    /**
     * 错误摘要脱敏：截断到列宽上限，并经共享 {@link SensitiveDataMasker} 清除
     * Bearer/JWT/预签名串等敏感片段，避免凭据随错误信息进入 {@code job_attempt.error_message}。
     */
    private String maskMessage(String message) {
        if (message == null) {
            return null;
        }
        String masked = masker.maskValue(message);
        return masked.length() > 1000 ? masked.substring(0, 1000) : masked;
    }

    /** 空计数器兜底（无 Micrometer 时，如单元测试）。 */
    private static final class NoopCounter implements Counter {
        @Override
        public void increment() {
        }

        @Override
        public void increment(double amount) {
        }

        @Override
        public double count() {
            return 0;
        }

        @Override
        public io.micrometer.core.instrument.Meter.Id getId() {
            return null;
        }
    }
}
