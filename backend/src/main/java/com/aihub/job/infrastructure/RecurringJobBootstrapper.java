/*
 * 功能: 周期任务引导——应用启动时幂等创建对账/处理类周期任务，确保 reconciler 定期执行。
 * 时间: 2026-07-08
 */
package com.aihub.job.infrastructure;

import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobRepository;
import com.aihub.job.domain.JobStatus;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 周期任务引导器。
 *
 * <p>应用启动时幂等创建对账/处理类周期任务。每个类型仅创建一次——若已存在同类型的
 * PENDING/RUNNING/RETRY_WAIT/SCHEDULED 任务则跳过。周期调度由 {@link JobWorker} 的
 * lease-recovery 机制驱动，无需额外的 {@code @Scheduled} 调度器。
 *
 * <p>引导的任务类型：
 * <ul>
 *   <li>{@code JOB_LEASE_RECONCILE}——租约到期任务恢复</li>
 *   <li>{@code OUTBOX_DELIVERY_RECONCILE}——Outbox 事件投递对账</li>
 *   <li>{@code WEBHOOK_PROCESS}——Webhook Inbox 处理</li>
 *   <li>{@code SESSION_STAGING_RECONCILE}——上传暂存区清理对账</li>
 *   <li>{@code MINIO_STORAGE_RECONCILE}——MinIO 存储对账</li>
 *   <li>{@code ASSET_REPO_RECONCILE}——资产仓库对账</li>
 *   <li>{@code PUBLISHED_VERSION_RECONCILE}——已发布版本三元组对账</li>
 * </ul>
 */
@Component
public class RecurringJobBootstrapper implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RecurringJobBootstrapper.class);

    private static final String SYSTEM_PRINCIPAL = "system";

    /** 周期任务类型与最大重试次数。 */
    private static final List<RecurringJobDef> RECURRING_JOBS = List.of(
            new RecurringJobDef("JOB_LEASE_RECONCILE", 10),
            new RecurringJobDef("OUTBOX_DELIVERY_RECONCILE", 5),
            new RecurringJobDef("WEBHOOK_PROCESS", 5),
            new RecurringJobDef("SESSION_STAGING_RECONCILE", 5),
            new RecurringJobDef("MINIO_STORAGE_RECONCILE", 5),
            new RecurringJobDef("ASSET_REPO_RECONCILE", 5),
            new RecurringJobDef("PUBLISHED_VERSION_RECONCILE", 5)
    );

    private final JobRepository jobRepository;
    private final IdGenerator idGenerator;

    public RecurringJobBootstrapper(JobRepository jobRepository, IdGenerator idGenerator) {
        this.jobRepository = jobRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (RecurringJobDef def : RECURRING_JOBS) {
            ensureRecurringJob(def);
        }
    }

    private void ensureRecurringJob(RecurringJobDef def) {
        try {
            // 检查是否已有活跃的同类型任务
            List<Job> existing = jobRepository.list(null, null, null, 1000);
            boolean alreadyActive = existing.stream()
                    .anyMatch(j -> def.type.equals(j.type()) && isActive(j.status()));
            if (alreadyActive) {
                LOG.debug("recurring job type={} already active, skipping", def.type);
                return;
            }

            Instant now = Instant.now();
            String jobId = idGenerator.generate(IdPrefix.JOB);
            Job job = new Job(
                    null, jobId, def.type, "{}",
                    JobStatus.PENDING, def.maxAttempts, 0, now,
                    null, null, null, SYSTEM_PRINCIPAL,
                    null, null, now, now, 0
            );
            jobRepository.insert(job);
            LOG.info("bootstrapped recurring job type={} jobId={}", def.type, jobId);
        } catch (Exception e) {
            LOG.warn("failed to bootstrap recurring job type={}, will retry on next startup",
                    def.type, e);
        }
    }

    private boolean isActive(JobStatus status) {
        return status == JobStatus.PENDING
                || status == JobStatus.RUNNING
                || status == JobStatus.RETRY_WAIT;
    }

    private record RecurringJobDef(String type, int maxAttempts) {
    }
}
