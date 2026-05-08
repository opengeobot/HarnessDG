package com.harnessdg.integration.dagster;

import com.harnessdg.integration.config.IntegrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * 功能：Dagster GraphQL 客户端
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DagsterClient {

    private final IntegrationConfig integrationConfig;
    private WebClient webClient;

    private WebClient getWebClient() {
        if (webClient == null) {
            IntegrationConfig.DagsterConfig config = integrationConfig.getDagster();
            this.webClient = WebClient.builder()
                    .baseUrl(config.getEndpoint() + config.getGraphqlPath())
                    .defaultHeader("Content-Type", "application/json")
                    .codecs(codecConfigurer -> codecConfigurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                    .build();
        }
        return webClient;
    }

    /**
     * 执行 GraphQL 查询
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> executeQuery(String query, Map<String, Object> variables) {
        Map<String, Object> body = Map.of(
                "query", query,
                "variables", variables != null ? variables : Map.of()
        );

        return getWebClient()
                .post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(result -> (Map<String, Object>) result)
                .timeout(Duration.ofMillis(integrationConfig.getDagster().getTimeoutMs()))
                .doOnSuccess(response -> log.debug("Dagster GraphQL response: {}", response))
                .doOnError(error -> log.error("Dagster GraphQL error: {}", error.getMessage()));
    }

    /**
     * 创建或更新 Asset 定义
     */
    public Mono<Map<String, Object>> createOrUpdateAsset(String assetKey, String groupName, String description) {
        String query = """
                mutation CreateOrUpdateAsset($assetKey: String!, $groupName: String, $description: String) {
                  createOrUpdateAsset(assetKey: $assetKey, groupName: $groupName, description: $description) {
                    assetKey
                    groupName
                    description
                  }
                }
                """;
        Map<String, Object> variables = Map.of(
                "assetKey", assetKey,
                "groupName", groupName,
                "description", description
        );
        return executeQuery(query, variables);
    }

    /**
     * 查询 Asset 信息
     */
    public Mono<Map<String, Object>> getAssetInfo(String assetKey) {
        String query = """
                query GetAssetInfo($assetKey: String!) {
                  assetOrError(assetKey: $assetKey) {
                    ... on Asset {
                      assetKey
                      description
                      groupName
                    }
                  }
                }
                """;
        Map<String, Object> variables = Map.of("assetKey", assetKey);
        return executeQuery(query, variables);
    }

    /**
     * 触发 Job 执行
     */
    public Mono<Map<String, Object>> triggerJobExecution(String jobName, String repositoryName) {
        String query = """
                mutation TriggerJobExecution($jobName: String!, $repositoryName: String!) {
                  launchPipelineExecution(jobName: $jobName, repositoryName: $repositoryName) {
                    ... on LaunchRunSuccess {
                      runId
                    }
                  }
                }
                """;
        Map<String, Object> variables = Map.of(
                "jobName", jobName,
                "repositoryName", repositoryName
        );
        return executeQuery(query, variables);
    }

    /**
     * 查询 Job 运行状态
     */
    public Mono<Map<String, Object>> getRunStatus(String runId) {
        String query = """
                query GetRunStatus($runId: String!) {
                  runOrError(runId: $runId) {
                    status
                  }
                }
                """;
        Map<String, Object> variables = Map.of("runId", runId);
        return executeQuery(query, variables);
    }
}
