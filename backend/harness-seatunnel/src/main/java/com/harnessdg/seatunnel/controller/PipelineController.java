/**
 * 功能：SeaTunnel Pipeline 控制器
 * 时间：2026-05-12
 * 作者：AxeXie
 *
 * 提供 Pipeline 配置生成、提交、状态查询等 HTTP 接口
 */
package com.harnessdg.seatunnel.controller;

import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.common.response.R;
import com.harnessdg.seatunnel.client.SeaTunnelClient;
import com.harnessdg.seatunnel.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * SeaTunnel Pipeline 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/seatunnel")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "harnessdg.seatunnel.enabled", havingValue = "true", matchIfMissing = false)
public class PipelineController {

    private final PipelineService pipelineService;

    /**
     * 生成 Pipeline 配置
     *
     * @param taskId 任务 ID
     * @return Pipeline 配置内容
     */
    @GetMapping("/pipeline/{taskId}")
    public R<String> generatePipeline(@PathVariable Long taskId) {
        try {
            String config = pipelineService.generatePipelineConfig(taskId);
            return R.ok(config);
        } catch (Exception e) {
            log.error("Failed to generate pipeline config for task: taskId={}", taskId, e);
            return R.fail(ErrorCode.INTERNAL_ERROR, "Failed to generate pipeline config: " + e.getMessage());
        }
    }

    /**
     * 提交 Pipeline 执行
     *
     * @param taskId 任务 ID
     * @return 提交结果（包含 jobId）
     */
    @PostMapping("/pipeline/{taskId}/submit")
    public Mono<R<SeaTunnelClient.SeaTunnelResponse>> submitPipeline(@PathVariable Long taskId) {
        return pipelineService.generateAndSubmit(taskId)
                .map(R::ok)
                .onErrorResume(error -> {
                    log.error("Failed to submit pipeline for task: taskId={}", taskId, error);
                    return Mono.just(R.fail(ErrorCode.INTERNAL_ERROR, "Failed to submit pipeline: " + error.getMessage()));
                });
    }

    /**
     * 查询作业状态
     *
     * @param jobId 作业 ID
     * @return 作业状态
     */
    @GetMapping("/job/{jobId}/status")
    public Mono<R<SeaTunnelClient.JobStatus>> getJobStatus(@PathVariable String jobId) {
        return pipelineService.getJobStatus(jobId)
                .map(R::ok)
                .onErrorResume(error -> {
                    log.error("Failed to get job status: jobId={}", jobId, error);
                    return Mono.just(R.fail(ErrorCode.INTERNAL_ERROR, "Failed to get job status: " + error.getMessage()));
                });
    }

    /**
     * 获取作业日志
     *
     * @param jobId 作业 ID
     * @return 日志内容
     */
    @GetMapping(value = "/job/{jobId}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> getJobLogs(@PathVariable String jobId) {
        return pipelineService.getJobLogs(jobId)
                .onErrorResume(error -> {
                    log.error("Failed to get job logs: jobId={}", jobId, error);
                    return Mono.just("Failed to get job logs: " + error.getMessage());
                });
    }

    /**
     * 停止作业
     *
     * @param jobId 作业 ID
     * @return 停止结果
     */
    @PostMapping("/job/{jobId}/stop")
    public Mono<R<SeaTunnelClient.SeaTunnelResponse>> stopJob(@PathVariable String jobId) {
        return pipelineService.stopJob(jobId)
                .map(R::ok)
                .onErrorResume(error -> {
                    log.error("Failed to stop job: jobId={}", jobId, error);
                    return Mono.just(R.fail(ErrorCode.INTERNAL_ERROR, "Failed to stop job: " + error.getMessage()));
                });
    }
}
