package com.modelhub.catalog.service;

import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.domain.JobEventEntity;
import com.modelhub.catalog.repo.JobEventRepository;
import com.modelhub.catalog.repo.JobRepository;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Jobs 查询面应用服务（ADR-001）：Controller 仅编排本服务，
 * job/job_events 仓库访问与取消状态迁移收敛在 catalog 模块内。
 */
@Service
public class JobQueryService {

    /** 05 §1 Job 终态集合（SSE 收口与取消校验共用）。 */
    public static final Set<String> TERMINAL_STATUSES =
            Set.of("succeeded", "failed", "cancelled", "dead_letter");

    private final JobRepository jobs;
    private final JobEventRepository events;
    private final JobEventService jobEventService;

    public JobQueryService(JobRepository jobs, JobEventRepository events, JobEventService jobEventService) {
        this.jobs = jobs;
        this.events = events;
        this.jobEventService = jobEventService;
    }

    public Optional<JobEntity> findByPublicId(UUID publicId) {
        return jobs.findByPublicId(publicId);
    }

    /** SSE 轮询探测当前状态；Job 并发消失视为终态（failed）。 */
    public String statusOf(Long internalJobId) {
        return jobs.findById(internalJobId).map(JobEntity::getStatus).orElse("failed");
    }

    /** 全量事件（Last-Event-ID 历史边界判定）。 */
    public List<JobEventEntity> eventsAsc(Long internalJobId) {
        return events.findByJobIdOrderBySequenceAsc(internalJobId);
    }

    /** 指定 sequence 之后的事件（历史回放 + 增量轮询）。 */
    public List<JobEventEntity> eventsAfter(Long internalJobId, Long fromSequence) {
        return events.findByJobIdAndSequenceGreaterThanOrderBySequenceAsc(internalJobId, fromSequence);
    }

    /**
     * 请求取消（幂等）：终态抛 409 CONFLICT；已 cancel_requested 重复取消直接返回当前视图；
     * 首次迁移置 cancel_requested 并写 status_changed 事件（同事务提交）。
     */
    @Transactional
    public JobEntity requestCancel(JobEntity job) {
        if (TERMINAL_STATUSES.contains(job.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "Job 已处于终态，无法取消");
        }
        if ("cancel_requested".equals(job.getStatus())) {
            return job;
        }
        job.setStatus("cancel_requested");
        job.setUpdatedAt(OffsetDateTime.now());
        job = jobs.save(job);
        jobEventService.record(job.getId(), "status_changed", Map.of("status", "cancel_requested"));
        return job;
    }
}
