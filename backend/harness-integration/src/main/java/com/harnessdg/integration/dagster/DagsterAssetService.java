package com.harnessdg.integration.dagster;

import com.harnessdg.integration.config.IntegrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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
}
