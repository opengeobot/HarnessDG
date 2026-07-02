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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 任务应用服务。
 *
 * <p>编排任务游标查询（按 created_at,id 键集）、人工重试（DEAD/RETRY_WAIT → PENDING）、人工取消
 * （仅 PENDING/RETRY_WAIT）与入队。授权判定在适配层完成（job:read / job:manage）。
 */
@Service
public class JobApplicationService {

    private final JobRepository jobRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public JobApplicationService(JobRepository jobRepository, IdGenerator idGenerator, Clock clock) {
        this.jobRepository = jobRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
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
     * 人工重试：将 DEAD/RETRY_WAIT 任务重置为 PENDING。
     */
    public void retryJob(String jobId) {
        requireJobExists(jobId);
        boolean reset = jobRepository.resetToPending(jobId, clock.instant());
        if (!reset) {
            throw new ConflictException(ErrorCode.JOB_STATE_NOT_ALLOWED,
                    "job is not in a retryable state (DEAD/RETRY_WAIT)",
                    java.util.Map.of("jobId", jobId));
        }
    }

    /**
     * 人工取消：仅 PENDING/RETRY_WAIT 可取消。
     */
    public void cancelJob(String jobId) {
        requireJobExists(jobId);
        boolean cancelled = jobRepository.cancel(jobId, clock.instant());
        if (!cancelled) {
            throw new ConflictException(ErrorCode.JOB_STATE_NOT_ALLOWED,
                    "job is not in a cancellable state (PENDING/RETRY_WAIT)",
                    java.util.Map.of("jobId", jobId));
        }
    }

    /**
     * 入队一个新任务（供内部业务模块调用，如 DVC/发布/Webhook 消费）。
     */
    public Job enqueue(String type, String payload, String principalId, String traceId,
                       String assetId, int maxAttempts) {
        Job job = new Job(null, idGenerator.generate(IdPrefix.JOB), type, payload, JobStatus.PENDING,
                maxAttempts, 0, clock.instant(), null, null, traceId, principalId, assetId, null,
                clock.instant(), clock.instant(), 0);
        jobRepository.insert(job);
        return job;
    }

    private void requireJobExists(String jobId) {
        if (jobRepository.findByJobId(jobId).isEmpty()) {
            throw new NotFoundException(ErrorCode.JOB_NOT_FOUND, "job not found", java.util.Map.of("jobId", jobId));
        }
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
