/*
 * 功能: 任务指标适配器，将 JobRepository 状态计数桥接为平台可观测性 JobMetricsPort。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.job.domain.JobRepository;
import com.aihub.platform.observability.domain.JobMetricsPort;
import org.springframework.stereotype.Component;

/**
 * 任务指标适配器。
 *
 * <p>实现平台可观测性层定义的 {@link JobMetricsPort}，将 {@link JobRepository#countByStatus}
 * 桥接给指标摘要服务。适配器位于任务模块基础设施层，保持可观测性层不直接依赖任务模块。
 */
@Component
public class JobMetricsAdapter implements JobMetricsPort {

    private final JobRepository jobRepository;

    public JobMetricsAdapter(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public long countByStatus(String status) {
        return jobRepository.countByStatus(status);
    }
}
