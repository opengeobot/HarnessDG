/*
 * 功能: 可靠任务 Worker 单元测试——验证领取、成功/失败/DEAD 状态机与通知触发。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.aihub.job.application.JobNotificationPort;
import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobAttemptRepository;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.job.domain.JobRepository;
import com.aihub.job.domain.JobStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 可靠任务 Worker 单元测试。
 */
class JobWorkerTest {

    private JobRepository jobRepository;
    private JobAttemptRepository jobAttemptRepository;
    private JobHandlerRegistry handlerRegistry;
    private BackoffCalculator backoff;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JobNotificationPort> notificationPortProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
    private JobNotificationPort notificationPort;
    private Clock clock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jobRepository = mock(JobRepository.class);
        jobAttemptRepository = mock(JobAttemptRepository.class);
        handlerRegistry = mock(JobHandlerRegistry.class);
        backoff = mock(BackoffCalculator.class);
        notificationPort = mock(JobNotificationPort.class);
        when(notificationPortProvider.getIfAvailable()).thenReturn(notificationPort);
        when(meterRegistryProvider.getIfAvailable()).thenReturn(null);
        clock = Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void shouldDoNothingWhenNoJobClaimed() {
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.empty());
        JobWorker worker = new JobWorker(jobRepository, jobAttemptRepository, handlerRegistry,
                backoff, notificationPortProvider, clock, meterRegistryProvider);
        worker.tick();
        verify(jobRepository, never()).markSucceeded(anyString(), any());
    }

    @Test
    void shouldMarkSucceededWhenHandlerSucceeds() throws Exception {
        Job job = createJob("job_1", "sample.noop", 0, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(job));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("sample.noop")).thenReturn(Optional.of(handler));

        JobWorker worker = new JobWorker(jobRepository, jobAttemptRepository, handlerRegistry,
                backoff, notificationPortProvider, clock, meterRegistryProvider);
        worker.tick();

        verify(jobRepository).markSucceeded(eq("job_1"), any());
        verify(jobAttemptRepository).insert(any());
        verify(handler).handle(any(JobContext.class));
    }

    @Test
    void shouldRetryWithBackoffWhenHandlerFails() throws Exception {
        Job job = createJob("job_2", "failing.type", 0, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(job));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("failing.type")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException("boom")).when(handler).handle(any(JobContext.class));
        Instant nextRun = Instant.parse("2026-07-01T10:00:10Z");
        when(backoff.nextRunAt(anyInt(), any())).thenReturn(nextRun);

        JobWorker worker = new JobWorker(jobRepository, jobAttemptRepository, handlerRegistry,
                backoff, notificationPortProvider, clock, meterRegistryProvider);
        worker.tick();

        verify(jobRepository).markRetryWait(eq("job_2"), eq(1), eq(nextRun), any());
        verify(jobRepository, never()).markDead(anyString(), anyString(), any());
    }

    @Test
    void shouldMarkDeadAndNotifyWhenMaxAttemptsExceeded() throws Exception {
        Job job = createJob("job_3", "failing.type", 2, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(job));
        JobHandler handler = mock(JobHandler.class);
        when(handlerRegistry.resolve("failing.type")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException("boom")).when(handler).handle(any(JobContext.class));

        JobWorker worker = new JobWorker(jobRepository, jobAttemptRepository, handlerRegistry,
                backoff, notificationPortProvider, clock, meterRegistryProvider);
        worker.tick();

        verify(jobRepository).markDead(eq("job_3"), anyString(), any());
        verify(notificationPort).notifyJobDead(eq("job_3"), eq("failing.type"), eq(3), anyString(), any());
    }

    @Test
    void shouldMarkDeadWhenNoHandlerRegistered() {
        Job job = createJob("job_4", "unknown.type", 0, 3);
        when(jobRepository.claimNext(anyString(), any(), anyLong())).thenReturn(Optional.of(job));
        when(handlerRegistry.resolve("unknown.type")).thenReturn(Optional.empty());

        JobWorker worker = new JobWorker(jobRepository, jobAttemptRepository, handlerRegistry,
                backoff, notificationPortProvider, clock, meterRegistryProvider);
        worker.tick();

        verify(jobRepository).markDead(eq("job_4"), eq("JOB_HANDLER_NOT_FOUND"), any());
        verify(notificationPort).notifyJobDead(eq("job_4"), eq("unknown.type"), eq(1),
                eq("JOB_HANDLER_NOT_FOUND"), any());
    }

    private Job createJob(String jobId, String type, int attempts, int maxAttempts) {
        return new Job(1L, jobId, type, "{}", JobStatus.PENDING, maxAttempts, attempts,
                Instant.now(), null, null, "trace-1", "usr_1", null, null,
                Instant.now(), Instant.now(), 0);
    }
}
