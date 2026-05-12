/**
 * 功能：SeaTunnel Web API 客户端
 * 时间：2026-05-12
 * 作者：AxeXie
 *
 * 封装 SeaTunnel Web 的 REST API 调用
 * 支持 Pipeline 提交、执行、状态查询
 */
package com.harnessdg.seatunnel.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * SeaTunnel Web API 客户端
 * 通过 SeaTunnel Web 的 REST API 提交和管理 Pipeline 执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeaTunnelClient {

    private final SeaTunnelConfig config;
    private WebClient webClient;

    /**
     * 获取或初始化 WebClient
     */
    private WebClient getWebClient() {
        if (webClient == null) {
            this.webClient = WebClient.builder()
                    .baseUrl(config.getEndpoint())
                    .defaultHeader("Content-Type", "application/json")
                    .build();
        }
        return webClient;
    }

    /**
     * 提交 Pipeline 执行
     *
     * @param pipelineConfig SeaTunnel 配置内容
     * @return 执行结果（包含 jobId）
     */
    public Mono<SeaTunnelResponse> submitPipeline(String pipelineConfig) {
        log.info("Submitting SeaTunnel pipeline");

        Map<String, Object> requestBody = Map.of(
                "config", pipelineConfig,
                "jobType", "BATCH"
        );

        return getWebClient()
                .post()
                .uri("/api/v1/job/submit")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(SeaTunnelResponse.class)
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .doOnSuccess(response -> log.info("Pipeline submitted successfully, jobId={}", response.jobId()))
                .doOnError(error -> log.error("Failed to submit pipeline", error));
    }

    /**
     * 查询作业执行状态
     *
     * @param jobId 作业 ID
     * @return 执行状态
     */
    public Mono<JobStatus> getJobStatus(String jobId) {
        log.info("Querying job status: jobId={}", jobId);

        return getWebClient()
                .get()
                .uri("/api/v1/job/status/{jobId}", jobId)
                .retrieve()
                .bodyToMono(JobStatus.class)
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .doOnSuccess(status -> log.info("Job status: jobId={}, status={}", jobId, status.status()))
                .doOnError(error -> log.error("Failed to query job status: jobId={}", jobId, error));
    }

    /**
     * 停止作业执行
     *
     * @param jobId 作业 ID
     * @return 停止结果
     */
    public Mono<SeaTunnelResponse> stopJob(String jobId) {
        log.info("Stopping job: jobId={}", jobId);

        return getWebClient()
                .post()
                .uri("/api/v1/job/stop/{jobId}", jobId)
                .retrieve()
                .bodyToMono(SeaTunnelResponse.class)
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .doOnSuccess(response -> log.info("Job stopped: jobId={}", jobId))
                .doOnError(error -> log.error("Failed to stop job: jobId={}", jobId, error));
    }

    /**
     * 获取作业执行日志
     *
     * @param jobId 作业 ID
     * @return 日志内容
     */
    public Mono<String> getJobLogs(String jobId) {
        log.info("Fetching job logs: jobId={}", jobId);

        return getWebClient()
                .get()
                .uri("/api/v1/job/logs/{jobId}", jobId)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .doOnSuccess(logs -> log.debug("Job logs fetched: jobId={}, size={}", jobId, logs.length()))
                .doOnError(error -> log.error("Failed to fetch job logs: jobId={}", jobId, error));
    }

    /**
     * SeaTunnel API 响应
     */
    public record SeaTunnelResponse(
            Long jobId,
            String status,
            String message
    ) {}

    /**
     * 作业状态
     */
    public record JobStatus(
            String jobId,
            String status,
            String jobName,
            String startTime,
            String endTime,
            String errorMessage
    ) {}
}
