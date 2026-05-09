package com.harnessdg.integration.dagster;

import com.harnessdg.integration.config.IntegrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 功能：Dagster Asset 管理服务
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DagsterAssetService {

    private final DagsterClient dagsterClient;
    private final IntegrationConfig integrationConfig;

    /**
     * 为指标创建 Dagster Asset
     */
    public Mono<Map<String, Object>> createMetricAsset(String metricCode, String metricName, String dataDomain) {
        String assetKey = String.format("metric_%s", metricCode);
        String description = String.format("Metric asset for %s in domain %s", metricName, dataDomain);
        return dagsterClient.createOrUpdateAsset(assetKey, dataDomain, description);
    }

    /**
     * 为实体创建 Dagster Asset
     */
    public Mono<Map<String, Object>> createEntityAsset(String entityCode, String entityName, String dataDomain) {
        String assetKey = String.format("entity_%s", entityCode);
        String description = String.format("Entity asset for %s in domain %s", entityName, dataDomain);
        return dagsterClient.createOrUpdateAsset(assetKey, dataDomain, description);
    }

    /**
     * 查询 Asset 信息
     */
    public Mono<Map<String, Object>> getAssetInfo(String assetKey) {
        return dagsterClient.getAssetInfo(assetKey);
    }

    /**
     * 触发 Job 执行
     */
    public Mono<Map<String, Object>> executeJob(String jobName, String repositoryName) {
        return dagsterClient.triggerJobExecution(jobName, repositoryName);
    }

    /**
     * 查询运行状态
     */
    public Mono<Map<String, Object>> getRunStatus(String runId) {
        return dagsterClient.getRunStatus(runId);
    }

    /**
     * 自动生成 Asset（根据本体定义）
     * 当创建 Entity 或 Metric 时自动调用
     */
    public Mono<Map<String, Object>> autoGenerateAsset(
            String assetType,
            String assetCode,
            String assetName,
            String dataDomain,
            Map<String, Object> metadata
    ) {
        String assetKey = String.format("%s_%s", assetType, assetCode);
        String description = String.format("Auto-generated %s asset for %s in domain %s",
                assetType, assetName, dataDomain);

        log.info("Auto-generating Dagster asset: {} (type={}, domain={})", assetKey, assetType, dataDomain);

        return dagsterClient.createOrUpdateAsset(assetKey, dataDomain, description)
                .doOnSuccess(result -> log.info("Asset created successfully: {}", assetKey))
                .doOnError(error -> log.error("Failed to create asset: {}", assetKey, error));
    }

    /**
     * 自动生成 Job（根据资产依赖关系）
     */
    public Mono<Map<String, Object>> autoGenerateJob(
            String jobName,
            String repositoryName,
            String[] assetKeys,
            String description
    ) {
        log.info("Auto-generating Dagster job: {} with {} assets", jobName, assetKeys.length);

        return dagsterClient.createJob(jobName, repositoryName, assetKeys, description)
                .doOnSuccess(result -> log.info("Job created successfully: {}", jobName))
                .doOnError(error -> log.error("Failed to create job: {}", jobName, error));
    }

    /**
     * 自动生成 Schedule（根据调度规则）
     */
    public Mono<Map<String, Object>> autoGenerateSchedule(
            String scheduleName,
            String jobName,
            String repositoryName,
            String cronExpression,
            String timezone
    ) {
        log.info("Auto-generating Dagster schedule: {} (cron={}, tz={})",
                scheduleName, cronExpression, timezone);

        return dagsterClient.createSchedule(scheduleName, jobName, repositoryName, cronExpression, timezone)
                .doOnSuccess(result -> log.info("Schedule created successfully: {}", scheduleName))
                .doOnError(error -> log.error("Failed to create schedule: {}", scheduleName, error));
    }

    /**
     * 完整的自动生成功能：Asset + Job + Schedule
     * 用于指标/实体创建时一键生成完整的 Dagster 配置
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Mono<Map<String, Object>> autoGenerateFullPipeline(
            String assetType,
            String assetCode,
            String assetName,
            String dataDomain,
            String scheduleCron,
            Map<String, Object> metadata
    ) {
        String assetKey = String.format("%s_%s", assetType, assetCode);
        String jobName = String.format("job_%s", assetCode);
        String scheduleName = String.format("schedule_%s", assetCode);
        String repositoryName = "harnessdg_repository";

        log.info("Auto-generating full pipeline for asset: {}", assetKey);

        // 1. 创建 Asset
        return autoGenerateAsset(assetType, assetCode, assetName, dataDomain, metadata)
                .flatMap(assetResult -> {
                    // 2. 创建 Job
                    return autoGenerateJob(jobName, repositoryName, new String[]{assetKey},
                                    String.format("Job for %s", assetKey))
                            .map(jobResult -> {
                                Map<String, Object> pipelineResult = new HashMap<>();
                                pipelineResult.put("asset", assetResult);
                                pipelineResult.put("job", jobResult);
                                pipelineResult.put("schedule", Map.of("status", "pending"));
                                return pipelineResult;
                            });
                })
                .flatMap(result -> {
                    // 3. 创建 Schedule（如果有调度规则）
                    if (scheduleCron != null && !scheduleCron.isEmpty()) {
                        return autoGenerateSchedule(scheduleName, jobName, repositoryName,
                                        scheduleCron, "UTC")
                                .map(scheduleResult -> {
                                    Map<String, Object> fullResult = new HashMap<>();
                                    Map<String, Object> resultMap = (Map<String, Object>) result;
                                    fullResult.putAll(resultMap);
                                    fullResult.put("schedule", scheduleResult);
                                    return fullResult;
                                });
                    }
                    return Mono.just(result);
                })
                .doOnSuccess(result -> log.info("Full pipeline generated for asset: {}", assetKey))
                .doOnError(error -> log.error("Failed to generate full pipeline for asset: {}", assetKey, error));
    }
}
