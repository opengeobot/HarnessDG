/**
 * 功能：OpenMetadata REST 客户端
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.integration.openmetadata;

import com.harnessdg.integration.config.IntegrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenMetadata 客户端封装
 * 提供元数据注册、血缘管理、标签管理等功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenMetadataClient {

    private final IntegrationConfig integrationConfig;
    private WebClient webClient;

    private WebClient getWebClient() {
        if (webClient == null) {
            IntegrationConfig.OpenMetadataConfig config = integrationConfig.getOpenMetadata();
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(config.getEndpoint() + config.getApiPath())
                    .defaultHeader("Content-Type", "application/json");

            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                builder.defaultHeader("X-OpenMetadata-ApiKey", config.getApiKey());
            }

            this.webClient = builder.build();
        }
        return webClient;
    }

    /**
     * 执行通用查询
     */
    public Mono<Map> executeQuery(String method, String path, Map<String, Object> body) {
        WebClient client = getWebClient();

        return client.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(integrationConfig.getOpenMetadata().getTimeoutMs()))
                .doOnSuccess(response -> log.debug("OpenMetadata response: {}", response))
                .doOnError(error -> log.error("OpenMetadata error: {}", error.getMessage()));
    }

    /**
     * 注册表元数据
     */
    public Mono<Map> registerTable(String tableName, String description, String database, String schema,
                                    List<Map<String, Object>> columns) {
        String path = "/tables";
        Map<String, Object> body = Map.of(
                "name", tableName,
                "description", description,
                "database", database,
                "schema", schema,
                "columns", columns
        );
        return executeQuery("POST", path, body);
    }

    /**
     * 更新表元数据
     */
    public Mono<Map> updateTable(String tableId, String description, List<String> tags) {
        String path = "/tables/" + tableId;
        Map<String, Object> body = Map.of(
                "description", description,
                "tags", tags
        );
        return executeQuery("PATCH", path, body);
    }

    /**
     * 注册血缘关系
     */
    public Mono<Map> registerLineage(String fromTable, String toTable, List<Map<String, Object>> columnMappings) {
        String path = "/lineage";
        Map<String, Object> body = Map.of(
                "fromEntity", Map.of("name", fromTable, "type", "table"),
                "toEntity", Map.of("name", toTable, "type", "table"),
                "columnLineage", columnMappings
        );
        return executeQuery("POST", path, body);
    }

    /**
     * 查询表信息
     */
    public Mono<Map> getTableInfo(String tableId) {
        String path = "/tables/" + tableId;
        return getWebClient()
                .get()
                .uri(path)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(integrationConfig.getOpenMetadata().getTimeoutMs()));
    }

    /**
     * 搜索表
     */
    public Mono<Map> searchTables(String query, int limit) {
        String path = "/search/tables?q=" + query + "&limit=" + limit;
        return getWebClient()
                .get()
                .uri(path)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(integrationConfig.getOpenMetadata().getTimeoutMs()));
    }

    /**
     * 添加标签
     */
    public Mono<Map> addTags(String entityType, String entityId, List<String> tags) {
        String path = "/" + entityType + "/" + entityId + "/tags";
        Map<String, Object> body = Map.of("tags", tags);
        return executeQuery("PUT", path, body);
    }

    /**
     * 注册指标元数据
     */
    public Mono<Map> registerMetric(String metricName, String description, String entityType,
                                     String expression, String unit) {
        String path = "/metrics";
        Map<String, Object> body = Map.of(
                "name", metricName,
                "description", description,
                "entityType", entityType,
                "expression", expression,
                "unit", unit
        );
        return executeQuery("POST", path, body);
    }

    /**
     * 健康检查
     */
    public Mono<Map> healthCheck() {
        return getWebClient()
                .get()
                .uri("/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(5000));
    }
}
