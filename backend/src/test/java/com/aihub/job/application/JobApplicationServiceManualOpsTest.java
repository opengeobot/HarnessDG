/*
 * 功能: 任务应用服务人工重试/取消的审计与前置条件回归（TASK-P0BR-024）。
 * 时间: 2026-09-21
 * 作者: AxeXie
 */
package com.aihub.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobRepository;
import com.aihub.job.domain.JobStatus;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 人工重试/取消的审计落点与状态前置条件回归（AC-P0B-JOB-005 的 E2 部分）。
 *
 * <p>固化三件事：成功迁移必须伴随对应审计；拒绝路径（404/409）绝不产生成功类审计；
 * 审计 actor 取自共享 PrincipalContext 而非业务模块解析 Token。
 */
class JobApplicationServiceManualOpsTest {

    private JobRepository jobRepository;
    private JobAuditPort auditPort;
    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        auditPort = mock(JobAuditPort.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T08:00:00Z"), ZoneOffset.UTC);
        service = new JobApplicationService(jobRepository, idGenerator, clock, auditPort);
    }

    @AfterEach
    void tearDown() {
        PrincipalContextHolder.clear();
    }

    private void withPrincipal(String principalId) {
        PrincipalContextHolder.set(new PrincipalContext(principalId, null, null, null,
                List.of(), Set.of(), Set.of(), 0, "zh-CN", "req_01", "trace_01"));
    }

    private Job existingJob(String jobId, JobStatus status, int attempts) {
        Instant now = Instant.parse("2026-09-21T08:00:00Z");
        return new Job(null, jobId, "REPOSITORY_PROVISION", "{}", status, 5, attempts, now,
                null, null, "trace_01", "usr_01", "ast_a", null, now, now, 0);
    }

    @Test
    @DisplayName("DEAD 任务人工重试成功：写 JOB_MANUALLY_RETRIED 审计且 actor 为当前主体")
    void retryWritesAudit() {
        withPrincipal("usr_ops");
        when(jobRepository.findByJobId("job_1")).thenReturn(Optional.of(existingJob("job_1", JobStatus.DEAD, 5)));
        when(jobRepository.resetToPending(eq("job_1"), any())).thenReturn(true);

        service.retryJob("job_1");

        verify(auditPort).record(eq("JOB_MANUALLY_RETRIED"), eq("job:retry"), eq("job_1"),
                eq("usr_ops"), any());
    }

    @Test
    @DisplayName("人工重试不重置 attempts：resetToPending 的 SQL 不携带 attempts 归零（语义固定见服务注释）")
    void retryDoesNotResetAttemptsBudget() {
        withPrincipal("usr_ops");
        when(jobRepository.findByJobId("job_2")).thenReturn(Optional.of(existingJob("job_2", JobStatus.DEAD, 5)));
        when(jobRepository.resetToPending(eq("job_2"), any())).thenReturn(true);

        service.retryJob("job_2");

        // 审计摘要必须显式携带 budgetReset=false，使「不发放新预算」成为可核对的事实。
        ArgumentCaptor<Map<String, Object>> attrs = ArgumentCaptor.forClass(Map.class);
        verify(auditPort).record(eq("JOB_MANUALLY_RETRIED"), anyString(), eq("job_2"), anyString(),
                attrs.capture());
        assertThat(attrs.getValue()).containsEntry("budgetReset", false);
    }

    @Test
    @DisplayName("PENDING 任务取消成功：写 JOB_MANUALLY_CANCELLED 审计")
    void cancelWritesAudit() {
        withPrincipal("usr_ops");
        when(jobRepository.findByJobId("job_3")).thenReturn(Optional.of(existingJob("job_3", JobStatus.PENDING, 0)));
        when(jobRepository.cancel(eq("job_3"), any())).thenReturn(true);

        service.cancelJob("job_3");

        verify(auditPort).record(eq("JOB_MANUALLY_CANCELLED"), eq("job:cancel"), eq("job_3"),
                eq("usr_ops"), any());
    }

    @Test
    @DisplayName("状态不允许的拒绝（409）不产生成功类审计")
    void rejectedTransitionWritesNoAudit() {
        withPrincipal("usr_ops");
        when(jobRepository.findByJobId("job_4")).thenReturn(Optional.of(existingJob("job_4", JobStatus.SUCCEEDED, 1)));
        when(jobRepository.resetToPending(eq("job_4"), any())).thenReturn(false);

        assertThatThrownBy(() -> service.retryJob("job_4")).isInstanceOf(ConflictException.class);
        verify(auditPort, never()).record(anyString(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("任务不存在（404）不产生审计、不触达状态迁移")
    void missingJobWritesNoAudit() {
        withPrincipal("usr_ops");
        when(jobRepository.findByJobId("job_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelJob("job_missing")).isInstanceOf(NotFoundException.class);
        verify(jobRepository, never()).cancel(anyString(), any());
        verify(auditPort, never()).record(anyString(), any(), anyString(), any(), any());
    }
}
