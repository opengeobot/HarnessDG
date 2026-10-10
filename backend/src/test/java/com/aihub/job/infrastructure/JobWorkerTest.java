/*
 * 功能: 可靠任务 Worker 单元测试——验证领取、成功/失败/DEAD 状态机、可重试判定、调度自愈与通知触发。
 * 时间: 2026-09-22
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.aihub.job.domain.JobStatus;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.error.DependencyException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.logging.SensitiveDataMasker;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 可靠任务 Worker 单元测试。
 */
class JobWorkerTest {

    private static final long LEASE_SECONDS = 60L;
    /** 既有状态机用例默认单领取（等价于修复前行为），批量语义由专门用例覆盖。 */
    private static final int DEFAULT_BATCH_SIZE = 1;

    private JobRepository jobRepository;
    private JobAttemptRepository jobAttemptRepository;
    private JobHandlerRegistry handlerRegistry;
    private BackoffCalculator backoff;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JobNotificationPort> notificationPortProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JobAuditPort> auditPortProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<PlatformMetrics> platformMetricsProvider = mock(ObjectProvider.class);
    private JobNotificationPort notificationPort;
    private JobAuditPort auditPort;
    private Clock clock;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        jobAttemptRepository = mock(JobAttemptRepository.class);
        handlerRegistry = mock(JobHandlerRegistry.class);
        backoff = mock(BackoffCalculator.class);
        notificationPort = mock(JobNotificationPort.class);
        auditPort = mock(JobAuditPort.class);
        when(notificationPortProvider.getIfAvailable()).thenReturn(notificationPort);
        when(auditPortProvider.getIfAvailable()).thenReturn(auditPort);
        when(meterRegistryProvider.getIfAvailable()).thenReturn(null);
        when(platformMetricsProvider.getIfAvailable()).thenReturn(null);
        when(jobAttemptRepository.exists(anyString(), anyInt())).thenReturn(false);
        clock = Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);
    }

    private JobWorker newWorker() {
        return newWorker(DEFAULT_BATCH_SIZE);
    }

    private JobWorker newWorker(int batchSize) {
        // 事务模板以 mock 事务管理器构造：execute 同步运行回调，使单测能观察
        // 「迁移 + attempt + 审计」是否确实处于同一执行单元内。
        TransactionTemplate txTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        return new JobWorker(jobRepository, jobAttemptRepository, handlerRegistry, backoff,
                notificationPortProvider, auditPortProvider, txTemplate, clock, meterRegistryProvider,
                new SensitiveDataMasker(), LEASE_SECONDS, batchSize, platformMetricsProvider);
    }

    @Test
    void shouldDoNothingWhenNoJobClaimed() {
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.empty());
        newWorker().tick();
        verify(jobRepository, never()).markSucceeded(anyString(), any());
    }

    @Test
    void shouldClaimWithConfiguredLeaseSeconds() {
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.empty());
        newWorker().tick();
        verify(jobRepository).claimNext(anyString(), any(), eq(LEASE_SECONDS));
    }

    @Test
    void shouldClaimUpToBatchSizeWithinOneTick() throws Exception {
        // 队列始终有任务：一个 tick 至多领取 batchSize 个，验证批量上限而非无限循环
        when(jobRepository.claimNext(anyString(), any(), anyLong()))
                .thenAnswer(inv -> Optional.of(claimed(claimedJob("job_x", "sample.noop", 1, 3))));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("sample.noop")).thenReturn(Optional.of(handler));

        newWorker(5).tick();

        verify(jobRepository, times(5)).claimNext(anyString(), any(), anyLong());
        verify(handler, times(5)).handle(any(JobContext.class));
    }

    @Test
    void shouldStopEarlyWhenQueueEmpties() throws Exception {
        // 前两次有任务、第三次起队列空：应提前结束，不再领满 batchSize
        when(jobRepository.claimNext(anyString(), any(), anyLong()))
                .thenReturn(Optional.of(claimed(claimedJob("job_a", "sample.noop", 1, 3))))
                .thenReturn(Optional.of(claimed(claimedJob("job_b", "sample.noop", 1, 3))))
                .thenReturn(Optional.empty());
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("sample.noop")).thenReturn(Optional.of(handler));

        newWorker(5).tick();

        verify(jobRepository, times(3)).claimNext(anyString(), any(), anyLong());
        verify(handler, times(2)).handle(any(JobContext.class));
    }

    @Test
    void shouldContinueBatchWhenHandlerFails() throws Exception {
        // 单个任务失败不得中断本批：后续任务仍应被领取处理
        when(jobRepository.claimNext(anyString(), any(), anyLong()))
                .thenReturn(Optional.of(claimed(claimedJob("job_f", "failing.type", 1, 3))))
                .thenReturn(Optional.of(claimed(claimedJob("job_g", "sample.noop", 1, 3))))
                .thenReturn(Optional.empty());
        when(backoff.nextRunAt(anyInt(), any())).thenReturn(Instant.parse("2026-07-01T10:00:02Z"));
        JobHandler failing = mock(JobHandler.class);
        doThrow(new RuntimeException("boom")).when(failing).handle(any(JobContext.class));
        JobHandler ok = mock(JobHandler.class);
        when(handlerRegistry.resolve("failing.type")).thenReturn(Optional.of(failing));
        when(handlerRegistry.resolve("sample.noop")).thenReturn(Optional.of(ok));

        newWorker(5).tick();

        verify(failing).handle(any(JobContext.class));
        verify(ok).handle(any(JobContext.class));
        verify(jobRepository).markRetryWait(eq("job_f"), any(), any());
        verify(jobRepository).markSucceeded(eq("job_g"), any());
    }

    @Test
    void shouldMarkSucceededWhenHandlerSucceeds() throws Exception {
        Job job = claimedJob("job_1", "sample.noop", 1, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("sample.noop")).thenReturn(Optional.of(handler));

        newWorker().tick();

        verify(jobRepository).markSucceeded(eq("job_1"), any());
        verify(handlerRegistry).resolve("sample.noop");
        verify(handler).handle(any(JobContext.class));
        assertThat(capturedAttemptStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldRetryWithBackoffWhenHandlerFails() throws Exception {
        Job job = claimedJob("job_2", "failing.type", 1, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("failing.type")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException("boom")).when(handler).handle(any(JobContext.class));
        Instant nextRun = Instant.parse("2026-07-01T10:00:10Z");
        when(backoff.nextRunAt(anyInt(), any())).thenReturn(nextRun);

        newWorker().tick();

        // 失败路径不再写回 attempts（已在领取时递增），避免与 attempt 唯一约束冲突。
        verify(jobRepository).markRetryWait(eq("job_2"), eq(nextRun), any());
        verify(jobRepository, never()).markDead(anyString(), anyString(), any());
    }

    @Test
    void shouldMarkDeadAndNotifyWhenMaxAttemptsExceeded() throws Exception {
        Job job = claimedJob("job_3", "failing.type", 3, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("failing.type")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException("boom")).when(handler).handle(any(JobContext.class));

        newWorker().tick();

        verify(jobRepository).markDead(eq("job_3"), eq("INTERNAL_ERROR"), any());
        verify(notificationPort).notifyJobDead(eq("job_3"), eq("failing.type"), eq(3),
                eq("INTERNAL_ERROR"), any());
        // JOB_DEAD 审计与状态迁移同一执行单元提交；摘要不含 payload 原文。
        verify(auditPort).record(eq("JOB_DEAD"), any(), eq("job_3"), any(),
                eq(Map.of("jobType", "failing.type", "attempts", 3, "errorCode", "INTERNAL_ERROR")));
    }

    @Test
    void shouldMarkDeadImmediatelyWhenErrorIsNotRetryable() throws Exception {
        Job job = claimedJob("job_5", "failing.type", 1, 5);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("failing.type")).thenReturn(Optional.of(handler));
        doThrow(new ValidationException(ErrorCode.ASSET_COORDINATE_INVALID, "invalid", Map.of()))
                .when(handler).handle(any(JobContext.class));

        newWorker().tick();

        // 不可重试错误不得进入 RETRY_WAIT，即使仍有剩余重试预算（INV-JOB-006）。
        verify(jobRepository).markDead(eq("job_5"), eq("ASSET_COORDINATE_INVALID"), any());
        verify(jobRepository, never()).markRetryWait(anyString(), any(), any());
    }

    @Test
    void shouldRetryWhenErrorIsRetryableAndBudgetRemains() throws Exception {
        Job job = claimedJob("job_6", "failing.type", 1, 5);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("failing.type")).thenReturn(Optional.of(handler));
        doThrow(new DependencyException(ErrorCode.ASSET_REPOSITORY_PROVISION_FAILED, "gitea down",
                Map.of(), null))
                .when(handler).handle(any(JobContext.class));
        when(backoff.nextRunAt(anyInt(), any())).thenReturn(Instant.parse("2026-07-01T10:00:10Z"));

        newWorker().tick();

        verify(jobRepository, never()).markDead(anyString(), anyString(), any());
        verify(jobRepository).markRetryWait(eq("job_6"), any(), any());
    }

    @Test
    void shouldMarkDeadWhenNoHandlerRegistered() {
        Job job = claimedJob("job_4", "unknown.type", 1, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        when(handlerRegistry.resolve("unknown.type")).thenReturn(Optional.empty());

        newWorker().tick();

        verify(jobRepository).markDead(eq("job_4"), eq("JOB_HANDLER_NOT_FOUND"), any());
        verify(notificationPort).notifyJobDead(eq("job_4"), eq("unknown.type"), eq(1),
                eq("JOB_HANDLER_NOT_FOUND"), any());
        verify(auditPort).record(eq("JOB_DEAD"), any(), eq("job_4"), any(),
                eq(Map.of("jobType", "unknown.type", "attempts", 1, "errorCode", "JOB_HANDLER_NOT_FOUND")));
    }

    @Test
    void shouldNotAuditJobDeadWhenRetrying() throws Exception {
        // 可重试失败进入 RETRY_WAIT：不得产生 JOB_DEAD 审计（事件只属于终态迁移）。
        Job job = claimedJob("job_8", "failing.type", 1, 5);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("failing.type")).thenReturn(Optional.of(handler));
        doThrow(new DependencyException(ErrorCode.ASSET_REPOSITORY_PROVISION_FAILED, "gitea down",
                Map.of(), null))
                .when(handler).handle(any(JobContext.class));
        when(backoff.nextRunAt(anyInt(), any())).thenReturn(Instant.parse("2026-07-01T10:00:10Z"));

        newWorker().tick();

        verify(auditPort, never()).record(anyString(), any(), anyString(), any(), any());
    }

    @Test
    void shouldRecordAbandonedAttemptWhenReclaimingExpiredLease() throws Exception {
        // 领取前状态为 RUNNING：上一个持有者被强制终止，本次为租约恢复。
        Job job = claimedJob("job_7", "sample.noop", 2, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong()))
                .thenReturn(Optional.of(new JobClaim(job, JobStatus.RUNNING, 1,
                        Instant.parse("2026-07-01T09:59:00Z"))));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("sample.noop")).thenReturn(Optional.of(handler));

        newWorker().tick();

        // 被终止的第 1 次尝试被补记为 FAILED/JOB_LEASE_EXPIRED，第 2 次尝试正常成功。
        ArgumentCaptor<JobAttempt> attempts = ArgumentCaptor.forClass(JobAttempt.class);
        verify(jobAttemptRepository, times(2)).insert(attempts.capture());
        assertThat(attempts.getAllValues()).extracting(JobAttempt::attemptNo).containsExactly(1, 2);
        assertThat(attempts.getAllValues()).extracting(JobAttempt::errorCode)
                .containsExactly(JobWorker.LEASE_EXPIRED_ERROR_CODE, null);
        assertThat(attempts.getAllValues()).extracting(JobAttempt::status)
                .containsExactly("FAILED", "SUCCESS");
        // started_at 在 job_attempt 中是 NOT NULL，被终止的尝试必须以真实时间补齐，
        // 否则写库抛 NPE 且租约恢复永远无法完成。
        assertThat(attempts.getAllValues()).extracting(JobAttempt::startedAt).doesNotContainNull();
        assertThat(attempts.getAllValues().get(0).startedAt())
                .as("被终止尝试的开始时刻取上一次领取时刻")
                .isEqualTo(Instant.parse("2026-07-01T09:59:00Z"));
    }

    @Test
    void shouldNotRecordAbandonedAttemptForFirstEverClaim() throws Exception {
        Job job = claimedJob("job_8", "sample.noop", 1, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong()))
                .thenReturn(Optional.of(new JobClaim(job, JobStatus.PENDING, 0, job.updatedAt())));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("sample.noop")).thenReturn(Optional.of(handler));

        newWorker().tick();

        ArgumentCaptor<JobAttempt> attempts = ArgumentCaptor.forClass(JobAttempt.class);
        verify(jobAttemptRepository).insert(attempts.capture());
        assertThat(attempts.getValue().attemptNo()).isEqualTo(1);
        assertThat(attempts.getValue().status()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldMaskSensitiveFragmentsInAttemptErrorMessage() throws Exception {
        Job job = claimedJob("job_9", "failing.type", 1, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("failing.type")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException("upstream rejected Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abc.def"))
                .when(handler).handle(any(JobContext.class));
        when(backoff.nextRunAt(anyInt(), any())).thenReturn(Instant.parse("2026-07-01T10:00:10Z"));

        newWorker().tick();

        ArgumentCaptor<JobAttempt> attempt = ArgumentCaptor.forClass(JobAttempt.class);
        verify(jobAttemptRepository).insert(attempt.capture());
        assertThat(attempt.getValue().errorMessage())
                .as("attempt 摘要不得原样留存 Bearer Token")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
                .contains(SensitiveDataMasker.MASK);
    }

    @Test
    void shouldPropagateTraceIdIntoAttemptRecord() throws Exception {
        Job job = claimedJob("job_10", "sample.noop", 1, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("sample.noop")).thenReturn(Optional.of(handler));

        newWorker().tick();

        ArgumentCaptor<JobAttempt> attempt = ArgumentCaptor.forClass(JobAttempt.class);
        verify(jobAttemptRepository).insert(attempt.capture());
        assertThat(attempt.getValue().traceId()).isEqualTo("trace-1");
    }

    @Test
    void shouldNotThrowWhenClaimingFailsSoSchedulingContinues() {
        // 数据库瞬时不可用：异常不得逸出 tick，否则 ScheduledThreadPoolExecutor 会静默取消周期任务。
        when(jobRepository.claimNext(anyString(), any(), anyLong()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> newWorker().tick()).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowWhenFailureHandlingCannotPersist() throws Exception {
        Job job = claimedJob("job_11", "failing.type", 1, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("failing.type")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException("boom")).when(handler).handle(any(JobContext.class));
        when(backoff.nextRunAt(anyInt(), any())).thenReturn(Instant.parse("2026-07-01T10:00:10Z"));
        doThrow(new IllegalStateException("database unavailable"))
                .when(jobRepository).markRetryWait(anyString(), any(), any());

        assertThatCode(() -> newWorker().tick()).doesNotThrowAnyException();
    }

    @Test
    void shouldSkipInsertWhenAttemptNumberAlreadyRecorded() throws Exception {
        Job job = claimedJob("job_12", "sample.noop", 1, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(claimed(job)));
        when(jobAttemptRepository.exists("job_12", 1)).thenReturn(true);
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("sample.noop")).thenReturn(Optional.of(handler));

        newWorker().tick();

        verify(jobAttemptRepository, never()).insert(any());
    }

    private String capturedAttemptStatus() {
        ArgumentCaptor<JobAttempt> attempt = ArgumentCaptor.forClass(JobAttempt.class);
        verify(jobAttemptRepository).insert(attempt.capture());
        return attempt.getValue().status();
    }

    private static JobClaim claimed(Job job) {
        return new JobClaim(job, JobStatus.PENDING, job.attempts() - 1, job.updatedAt());
    }

    /**
     * 构造一个「已被领取」的任务：{@code attempts} 已在领取时递增到本次尝试序号。
     */
    private Job claimedJob(String jobId, String type, int attempts, int maxAttempts) {
        return new Job(1L, jobId, type, "{}", JobStatus.RUNNING, maxAttempts, attempts,
                Instant.now(), Instant.now().plusSeconds(LEASE_SECONDS), "worker-test", "trace-1",
                "usr_1", null, null, Instant.now(), Instant.now(), 1);
    }
}
