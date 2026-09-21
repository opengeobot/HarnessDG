/*
 * 功能: 任务入队的 trace 上下文填充回归测试（TASK-P0BR-063）。
 * 时间: 2026-09-20
 * 作者: AxeXie
 */
package com.aihub.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobRepository;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code REQ-JOB-001}「任务传递 trace 上下文」的入队侧回归。
 *
 * <p>历史缺陷：调用点硬编码 traceId=null，使 job_task/job_attempt 的 trace_id 恒为 NULL，
 * attempt 级追踪与 AC-P0B-AUD-004 的按 trace 关联整体失效。
 * 本测试固化取值优先级（显式传入 &gt; 共享 PrincipalContext &gt; 兜底生成）
 * 与「永不为 NULL」不变量。
 */
class JobApplicationServiceEnqueueTest {

    private JobRepository jobRepository;
    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate(any(IdPrefix.class))).thenReturn("job_test");
        Clock clock = Clock.fixed(Instant.parse("2026-09-20T08:00:00Z"), ZoneOffset.UTC);
        service = new JobApplicationService(jobRepository, idGenerator, clock, mock(JobAuditPort.class));
    }

    @AfterEach
    void tearDown() {
        // ThreadLocal 上下文必须成对清理，避免用例间串台。
        PrincipalContextHolder.clear();
    }

    private Job capturedJob() {
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("调用方显式传入 traceId 时被尊重，不被上下文或兜底覆盖")
    void explicitTraceIdWins() {
        PrincipalContextHolder.set(contextWithTrace("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        service.enqueue("REPOSITORY_PROVISION", "{}", "usr_01", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", null, 5);

        assertThat(capturedJob().traceId()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    }

    @Test
    @DisplayName("未显式传入时取当前请求共享 PrincipalContext 的 traceId")
    void fallsBackToPrincipalContextTraceId() {
        PrincipalContextHolder.set(contextWithTrace("cccccccccccccccccccccccccccccccc"));

        service.enqueue("VERSION_PUBLISH", "{}", "usr_01", null, "ver_01", 3);

        assertThat(capturedJob().traceId()).isEqualTo("cccccccccccccccccccccccccccccccc");
    }

    @Test
    @DisplayName("上下文缺失时兜底生成非空 32 位 hex，trace_id 永不为 NULL")
    void generatesNonBlankTraceIdWithoutContext() {
        service.enqueue("REPOSITORY_PROVISION", "{}", "usr_01", null, "ast_01", 5);

        String traceId = capturedJob().traceId();
        assertThat(traceId)
                .as("trace 必须非空且与 PrincipalContextFilter 同格式（32 位小写 hex），"
                        + "否则按 trace 关联查询将再次拿到 NULL")
                .isNotNull()
                .matches("^[0-9a-f]{32}$");
    }

    @Test
    @DisplayName("上下文存在但 traceId 为空白时同样兜底，不写入空串")
    void blankContextTraceIdStillFallsBack() {
        PrincipalContextHolder.set(contextWithTrace("  "));

        service.enqueue("REPOSITORY_PROVISION", "{}", "usr_01", "", null, 5);

        assertThat(capturedJob().traceId()).isNotBlank().matches("^[0-9a-f]{32}$");
    }

    @Test
    @DisplayName("两次无上下文入队获得不同 traceId（关联标识不得共串）")
    void generatedTraceIdsAreDistinctPerEnqueue() {
        service.enqueue("REPOSITORY_PROVISION", "{}", "usr_01", null, "ast_a", 5);
        String first = capturedJob().traceId();

        jobRepository = mock(JobRepository.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate(any(IdPrefix.class))).thenReturn("job_test2");
        service = new JobApplicationService(jobRepository, idGenerator,
                Clock.fixed(Instant.now(), ZoneOffset.UTC), mock(JobAuditPort.class));
        service.enqueue("REPOSITORY_PROVISION", "{}", "usr_01", null, "ast_b", 5);
        String second = capturedJob().traceId();

        assertThat(first).isNotEqualTo(second);
    }

    private static PrincipalContext contextWithTrace(String traceId) {
        return new PrincipalContext("usr_01", null, null, null,
                List.of(), Set.of(), Set.of(), 0, "zh-CN", "req_01", traceId);
    }
}
