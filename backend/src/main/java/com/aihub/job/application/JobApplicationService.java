/*
 * 功能: 任务应用服务，编排任务游标查询、人工重试/取消与入队，供 REST/Worker 复用。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.application;

import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobRepository;
import com.aihub.job.domain.JobStatus;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务应用服务。
 *
 * <p>编排任务游标查询（按 created_at,id 键集）、人工重试（DEAD/RETRY_WAIT → PENDING）、人工取消
 * （仅 PENDING/RETRY_WAIT）与入队。授权判定在适配层完成（job:read / job:manage）。
 */
@Service
public class JobApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(JobApplicationService.class);

    private final JobRepository jobRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final JobAuditPort auditPort;

    public JobApplicationService(JobRepository jobRepository, IdGenerator idGenerator, Clock clock,
                                 JobAuditPort auditPort) {
        this.jobRepository = jobRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.auditPort = auditPort;
    }

    /**
     * 游标查询任务列表。
     *
     * @param status 状态过滤（可空）
     * @param cursor 游标（可空）
     * @param limit  每页条数
     * @return 游标分页结果
     */
    public CursorPage<JobView> listJobs(String status, String cursor, int limit) {
        Cursor cursorDecoded = Cursor.decode(cursor);
        List<Job> jobs = jobRepository.list(status, cursorDecoded.time(), cursorDecoded.id(), limit + 1);
        boolean hasMore = jobs.size() > limit;
        List<JobView> views = jobs.stream().limit(limit).map(JobView::from).toList();
        String nextCursor = null;
        if (hasMore) {
            Job last = jobs.get(limit - 1);
            nextCursor = Cursor.encode(last.createdAt(), last.id());
        }
        return new CursorPage<>(views, nextCursor, hasMore);
    }

    /**
     * 人工重试：将 DEAD/RETRY_WAIT 任务重置为 PENDING，并与审计原子提交。
     *
     * <p><b>重试预算语义（TASK-P0BR-024 固定）：人工重试不重置 {@code attempts}</b>。
     * 理由：attempts 是「该任务已被自动执行过多少次」的事实记录（INV-JOB-005 单调递增），
     * 重置会使 DEAD 判定（attempts >= max_attempts）失去历史依据，并让
     * 「无限人工重试」绕过重试预算这一治理约束；运维的正确动作是修复依赖后重试，
     * 若仍反复 DEAD 应经告警升级处置，而不是由重试操作本身发放新预算。
     * 因此 DEAD 任务被人工重试后，Handler 再失败一次即回到 DEAD——这是设计后果，非缺陷。
     */
    @Transactional
    public void retryJob(String jobId) {
        requireJobExists(jobId);
        boolean reset = jobRepository.resetToPending(jobId, clock.instant());
        if (!reset) {
            throw new ConflictException(ErrorCode.JOB_STATE_NOT_ALLOWED,
                    "job is not in a retryable state (DEAD/RETRY_WAIT)",
                    java.util.Map.of("jobId", jobId));
        }
        auditPort.record("JOB_MANUALLY_RETRIED", "job:retry", jobId, currentPrincipalId(),
                java.util.Map.of("targetStatus", "PENDING", "budgetReset", false));
    }

    /**
     * 人工取消：仅 PENDING/RETRY_WAIT 可取消，并与审计原子提交。
     */
    @Transactional
    public void cancelJob(String jobId) {
        requireJobExists(jobId);
        boolean cancelled = jobRepository.cancel(jobId, clock.instant());
        if (!cancelled) {
            throw new ConflictException(ErrorCode.JOB_STATE_NOT_ALLOWED,
                    "job is not in a cancellable state (PENDING/RETRY_WAIT)",
                    java.util.Map.of("jobId", jobId));
        }
        auditPort.record("JOB_MANUALLY_CANCELLED", "job:cancel", jobId, currentPrincipalId(),
                java.util.Map.of("targetStatus", "CANCELLED"));
    }

    /**
     * 入队一个新任务（供内部业务模块调用，如 DVC/发布/Webhook 消费）。
     *
     * <p><b>trace 必须非空</b>：{@code REQ-JOB-001} 要求任务传递 trace 上下文，
     * 且 {@code AC-P0B-AUD-004} 依赖 {@code job_task/job_attempt.trace_id} 把
     * 「单请求触发的 DB + 外部依赖 + 后台任务」串成一条链。取值优先级：
     * 调用方显式传入 &gt; 当前请求的共享 PrincipalContext &gt; 新生成的 32 位 hex。
     * 兜底生成保证该列永不为 NULL（历史缺陷正是各调用点硬编码 null，
     * 使 attempt 级追踪与「按 trace 关联」整体失效）；兜底命中时记 debug 日志，
     * 便于发现漏传 trace 的新调用点。
     */
    public Job enqueue(String type, String payload, String principalId, String traceId,
                       String assetId, int maxAttempts) {
        Job job = new Job(null, idGenerator.generate(IdPrefix.JOB), type, payload, JobStatus.PENDING,
                maxAttempts, 0, clock.instant(), null, null, resolveTraceId(traceId, type),
                principalId, assetId, null, clock.instant(), clock.instant(), 0);
        jobRepository.insert(job);
        return job;
    }

    /**
     * 解析任务应携带的 traceId：显式传入优先，其次当前请求上下文，最后新生成。
     *
     * <p>不解析任何 Token；只读取共享 {@link PrincipalContextHolder}（平台规则允许的业务侧取值方式）。
     */
    private String resolveTraceId(String explicitTraceId, String jobType) {
        if (explicitTraceId != null && !explicitTraceId.isBlank()) {
            return explicitTraceId;
        }
        String fromContext = PrincipalContextHolder.current()
                .map(PrincipalContext::traceId)
                .filter(value -> !value.isBlank())
                .orElse(null);
        if (fromContext != null) {
            return fromContext;
        }
        String generated = UUID.randomUUID().toString().replace("-", "");
        LOG.debug("job enqueue without trace context; generated fallback type={} traceId={}", jobType, generated);
        return generated;
    }

    private void requireJobExists(String jobId) {
        if (jobRepository.findByJobId(jobId).isEmpty()) {
            throw new NotFoundException(ErrorCode.JOB_NOT_FOUND, "job not found", java.util.Map.of("jobId", jobId));
        }
    }

    /** 人工操作的 actor：只读共享主体上下文，不在业务模块解析 Token。 */
    private static String currentPrincipalId() {
        return PrincipalContextHolder.current().map(PrincipalContext::principalId).orElse(null);
    }

    /** 游标（不透明，base64 编码 occurredAt|id）。 */
    private record Cursor(Instant time, Long id) {
        static Cursor decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return new Cursor(null, null);
            }
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = decoded.indexOf('|');
            if (sep < 0) {
                return new Cursor(null, null);
            }
            Instant time = Instant.parse(decoded.substring(0, sep));
            Long id = Long.valueOf(decoded.substring(sep + 1));
            return new Cursor(time, id);
        }

        static String encode(Instant time, Long id) {
            String raw = time.toString() + "|" + id;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}
