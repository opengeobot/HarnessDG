/**
 * 功能：SeaTunnel Pipeline 服务
 * 时间：2026-05-12
 * 作者：AxeXie
 *
 * 提供 Pipeline 生成、提交、状态查询等业务逻辑
 */
package com.harnessdg.seatunnel.service;

import com.harnessdg.datasource.service.DataSourceService;
import com.harnessdg.model.datasource.dto.DataSourceDTO;
import com.harnessdg.model.datasource.dto.IngestionTaskDTO;
import com.harnessdg.seatunnel.client.SeaTunnelClient;
import com.harnessdg.seatunnel.template.PipelineTemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * SeaTunnel Pipeline 服务
 * 负责 Pipeline 配置生成、提交执行和状态管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "harnessdg.seatunnel.enabled", havingValue = "true", matchIfMissing = false)
public class PipelineService {

    private final PipelineTemplateEngine templateEngine;
    private final SeaTunnelClient seaTunnelClient;
    private final DataSourceService dataSourceService;

    /**
     * 为任务生成 Pipeline 配置
     *
     * @param taskId 任务 ID
     * @return Pipeline 配置内容
     */
    public String generatePipelineConfig(Long taskId) {
        log.info("Generating pipeline config for task: taskId={}", taskId);

        IngestionTaskDTO task = dataSourceService.getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: taskId=" + taskId);
        }

        DataSourceDTO source = dataSourceService.getDataSource(task.getSourceId());
        if (source == null) {
            throw new IllegalArgumentException("Data source not found: sourceId=" + task.getSourceId());
        }

        return templateEngine.generatePipelineConfig(source, task);
    }

    /**
     * 生成并提交 Pipeline
     *
     * @param taskId 任务 ID
     * @return 提交结果
     */
    public Mono<SeaTunnelClient.SeaTunnelResponse> generateAndSubmit(Long taskId) {
        log.info("Generating and submitting pipeline for task: taskId={}", taskId);

        String pipelineConfig = generatePipelineConfig(taskId);

        return seaTunnelClient.submitPipeline(pipelineConfig)
                .doOnSuccess(response -> log.info("Pipeline submitted: taskId={}, jobId={}", taskId, response.jobId()))
                .doOnError(error -> log.error("Failed to submit pipeline: taskId={}", taskId, error));
    }

    /**
     * 查询作业状态
     *
     * @param jobId 作业 ID
     * @return 作业状态
     */
    public Mono<SeaTunnelClient.JobStatus> getJobStatus(String jobId) {
        return seaTunnelClient.getJobStatus(jobId);
    }

    /**
     * 获取作业日志
     *
     * @param jobId 作业 ID
     * @return 日志内容
     */
    public Mono<String> getJobLogs(String jobId) {
        return seaTunnelClient.getJobLogs(jobId);
    }

    /**
     * 停止作业
     *
     * @param jobId 作业 ID
     * @return 停止结果
     */
    public Mono<SeaTunnelClient.SeaTunnelResponse> stopJob(String jobId) {
        return seaTunnelClient.stopJob(jobId);
    }
}
