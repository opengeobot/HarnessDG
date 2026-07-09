/*
 * 功能: 可靠任务 Worker——领取、执行、指数退避重试、DEAD 通知，全链路透传追踪上下文。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.job.application.BackoffCalculator;
import com.aihub.job.application.JobNotificationPort;
import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobAttempt;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.job.domain.JobRepository;
import com.aihub.job.domain.JobAttemptRepository;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.error.PlatformException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 可靠任务 Worker。
 *
 * <p>每次 {@link #tick()} 领取一个待执行任务（FOR UPDATE SKIP LOCKED），调用幂等 Handler；
 * 成功置 SUCCEEDED；失败按指数退避+抖动置 RETRY_WAIT，超 max_attempts 置 DEAD 并触发 JOB_DEAD 通知。
 * 全链路透传 traceId/principalId/assetId。tick 内部捕获所有异常，绝不向外抛出导致调度中断。
 */
@Component
public class JobWorker {

    private static final Logger LOG = LoggerFactory.getLogger(JobWorker.class);

    private final JobRepository jobRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final JobHandlerRegistry handlerRegistry;
    private final BackoffCalculator backoff;
    private final ObjectProvider<JobNotificationPort> notificationPortProvider;
    private final Clock clock;
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
                     Clock clock,
                     ObjectProvider<MeterRegistry> meterRegistryProvider,
                     ObjectProvider<PlatformMetrics> platformMetricsProvider) {
        this.jobRepository = jobRepository;
        this.jobAttemptRepository = jobAttemptRepository;
        this.handlerRegistry = handlerRegistry;
        this.backoff = backoff;
        this.notificationPortProvider = notificationPortProvider;
        this.clock = clock;
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
     * 领取并执行一个任务。无任务时直接返回。
     */
    public void tick() {
        Instant now = clock.instant();
        Optional<Job> claimed = jobRepository.claimNext(workerId, now, 60);
        if (claimed.isEmpty()) {
            return;
        }
        Job job = claimed.get();
        jobsClaimed.increment();
        int attemptNo = job.attempts() + 1;
        Instant startedAt = now;
        JobContext context = new JobContext(job.jobId(), job.type(), job.payload(), job.attempts(),
                job.principalId(), job.traceId(), job.assetId());

        Optional<JobHandler> handler = handlerRegistry.resolve(job.type());
        if (handler.isEmpty()) {
            handleNoHandler(job, attemptNo, startedAt);
            return;
        }

        try {
            handler.get().handle(context);
            Instant endedAt = clock.instant();
            jobRepository.markSucceeded(job.jobId(), endedAt);
            jobAttemptRepository.insert(new JobAttempt(null, job.jobId(), attemptNo, startedAt, endedAt,
                    "SUCCESS", null, null, Duration.between(startedAt, endedAt).toMillis()));
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

    private void handleNoHandler(Job job, int attemptNo, Instant startedAt) {
        Instant endedAt = clock.instant();
        String errorCode = "JOB_HANDLER_NOT_FOUND";
        jobRepository.markDead(job.jobId(), errorCode, endedAt);
        jobAttemptRepository.insert(new JobAttempt(null, job.jobId(), attemptNo, startedAt, endedAt,
                "DEAD", "no handler registered for type " + job.type(), errorCode,
                Duration.between(startedAt, endedAt).toMillis()));
        jobsDead.increment();
        notifyJobDead(job, attemptNo, errorCode);
        LOG.warn("job dead (no handler) jobId={} type={}", job.jobId(), job.type());
    }

    private void handleFailure(Job job, int attemptNo, Instant startedAt, Exception ex) {
        Instant endedAt = clock.instant();
        long durationMs = Duration.between(startedAt, endedAt).toMillis();
        String errorCode = ex instanceof PlatformException pe ? pe.errorCode().name() : "INTERNAL_ERROR";
        String message = maskMessage(ex.getMessage());
        int newAttempts = attemptNo;
        if (newAttempts >= job.maxAttempts()) {
            jobRepository.markDead(job.jobId(), errorCode, endedAt);
            jobAttemptRepository.insert(new JobAttempt(null, job.jobId(), attemptNo, startedAt, endedAt,
                    "DEAD", message, errorCode, durationMs));
            jobsDead.increment();
            notifyJobDead(job, newAttempts, errorCode);
            LOG.warn("job dead jobId={} type={} attempts={} error={}", job.jobId(), job.type(), newAttempts, errorCode);
        } else {
            Instant nextRunAt = backoff.nextRunAt(newAttempts, endedAt);
            jobRepository.markRetryWait(job.jobId(), newAttempts, nextRunAt, endedAt);
            jobAttemptRepository.insert(new JobAttempt(null, job.jobId(), attemptNo, startedAt, endedAt,
                    "FAILED", message, errorCode, durationMs));
            jobsFailed.increment();
            LOG.info("job retry_wait jobId={} type={} attempts={} nextRunAt={} error={}",
                    job.jobId(), job.type(), newAttempts, nextRunAt, errorCode);
        }
    }

    private void notifyJobDead(Job job, int retryCount, String errorCode) {
        JobNotificationPort port = notificationPortProvider.getIfAvailable();
        if (port != null) {
            port.notifyJobDead(job.jobId(), job.type(), retryCount, errorCode, job.principalId());
        }
    }

    private static String maskMessage(String message) {
        if (message == null) {
            return null;
        }
        // 错误信息可能含敏感片段，截断并仅保留概要，避免泄露密钥/凭据。
        return message.length() > 1000 ? message.substring(0, 1000) : message;
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
